import React from 'react';
import { apiFetch, signOut } from '../api';
import { useAuth } from '../auth';
import { usePageMeta } from '../utils/pageMeta';
import { getUnitPreference } from '../utils/units';

export default function Settings() {
  const { user, setUser } = useAuth();
  const { unitSystem: currentUnitSystem, distanceUnit, weightUnit } = getUnitPreference(user?.unitSystem);

  usePageMeta({ title: 'Momentum — Settings', description: 'Update your Momentum account settings.' });

  const [unitSystem, setUnitSystem] = React.useState(currentUnitSystem);
  const [unitsSaving, setUnitsSaving] = React.useState(false);
  const [unitsError, setUnitsError] = React.useState('');
  const [unitsSuccess, setUnitsSuccess] = React.useState('');
  const [deleteSaving, setDeleteSaving] = React.useState(false);
  const [deleteError, setDeleteError] = React.useState('');

  const isUnitsDirty = unitSystem !== currentUnitSystem;

  React.useEffect(() => {
    setUnitSystem(currentUnitSystem);
  }, [currentUnitSystem]);

  async function onUnitsSubmit(e) {
    e.preventDefault();
    setUnitsError('');
    setUnitsSuccess('');

    setUnitsSaving(true);
    try {
      const resp = await apiFetch('/settings/preferences/unit-system', {
        method: 'POST',
        body: JSON.stringify({ unitSystem }),
      });
      const updatedUser = resp?.user || null;
      if (updatedUser) setUser(updatedUser);
      setUnitsSuccess(resp?.message || 'Units updated');
    } catch (err) {
      setUnitsError(err?.message || 'Failed to update units');
    } finally {
      setUnitsSaving(false);
    }
  }

  async function onDeleteAccount() {
    setDeleteError('');
    const confirmed = window.confirm('Delete your account permanently? This will remove all workouts and cannot be undone.');
    if (!confirmed) return;

    setDeleteSaving(true);
    try {
      try {
        await apiFetch('/settings/account', { method: 'DELETE' });
      } catch (err) {
        if (err?.status !== 403) throw err;
        // Some deployments block DELETE at the proxy layer; fall back to POST.
        await apiFetch('/settings/account/delete', { method: 'POST' });
      }
      await signOut();
    } catch (err) {
      setDeleteError(err?.message || 'Failed to delete account');
    } finally {
      setDeleteSaving(false);
    }
  }

  return (
    <div className="main-content">
      <div className="page page-narrow settings-page">
        <header className="page-head">
          <div>
            <p className="kicker">Account</p>
            <h2>Settings</h2>
            <p className="settings-page__subtitle">Manage your account security and data.</p>
          </div>
        </header>

        <div className="settings-card">
          <div className="settings-card__title">Units</div>
          <div className="settings-help">
            Choose how workout distance and weight are displayed. Current labels: {distanceUnit} for cardio distance and {weightUnit} for lifting weight.
          </div>
          <form onSubmit={onUnitsSubmit} className="workout-form settings-form">
            <label>
              Unit system
              <select value={unitSystem} onChange={(e) => setUnitSystem(e.target.value)}>
                <option value="metric">Metric (km, kg)</option>
                <option value="imperial">Imperial (mi, lb)</option>
              </select>
            </label>

            {unitsError ? <div className="error-message">{unitsError}</div> : null}
            {unitsSuccess ? <div className="success-message">{unitsSuccess}</div> : null}

            <div className="settings-actions">
              <button type="submit" className="btn-primary" disabled={unitsSaving || !isUnitsDirty}>
                {unitsSaving ? 'Saving...' : 'Update units'}
              </button>
            </div>
          </form>
        </div>

        <div className="settings-card settings-card--security">
          <div className="settings-card__title">Security</div>
          <div className="settings-help">
            Signed in as: <strong>{user?.username || 'N/A'}</strong>
          </div>
          <div className="settings-help">Your sign-in is managed by your shared account.</div>
        </div>

        <div className="settings-card settings-card--danger">
          <div className="settings-card__title">Danger Zone</div>
          <div className="settings-help">
            Deleting your account permanently removes your workout history and cannot be undone.
          </div>
          {deleteError ? <div className="error-message">{deleteError}</div> : null}
          <button
            type="button"
            className="btn-danger"
            onClick={onDeleteAccount}
            disabled={deleteSaving}
          >
            {deleteSaving ? 'Deleting...' : 'Delete account'}
          </button>
        </div>
      </div>
    </div>
  );
}
