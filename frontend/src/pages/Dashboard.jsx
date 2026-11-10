import React from 'react';
import { apiFetch } from '../api';
import { useAuth } from '../auth';
import LineChart from '../components/LineChart';
import { formatMinutesAsClock } from '../utils/duration';
import { usePageMeta } from '../utils/pageMeta';
import { formatDisplayNumber, getUnitPreference } from '../utils/units';

function flattenWorkoutMap(workoutMap) {
  const entries = Object.entries(workoutMap || {});
  entries.sort(([a], [b]) => a.localeCompare(b));
  return entries;
}

export default function Dashboard() {
  const { user } = useAuth();
  const [data, setData] = React.useState(null);
  const [error, setError] = React.useState('');
  const [loading, setLoading] = React.useState(true);
  const [dateRange, setDateRange] = React.useState('all');
  const { distanceUnit, weightUnit, paceUnit } = getUnitPreference(user?.unitSystem);

  usePageMeta({ title: 'Momentum — Dashboard', description: 'Momentum dashboard and workout charts.' });

  const abortRef = React.useRef(null);

  const workoutMap = data?.workoutMap || {};
  const workoutType = data?.workoutType || '';
  const workoutName = data?.workoutName || '';

  const sortedWorkoutTypes = React.useMemo(() => flattenWorkoutMap(workoutMap), [workoutMap]);

  async function load(params) {
    setError('');
    setLoading(true);
    abortRef.current?.abort?.();
    const controller = new AbortController();
    abortRef.current = controller;
    try {
      const allParams = { dateRange, ...params };
      const qs = `?${new URLSearchParams(allParams).toString()}`;
      const resp = await apiFetch(`/dashboard${qs}`, { signal: controller.signal });
      setData(resp);
    } catch (err) {
      if (err?.name === 'AbortError') return;
      setError(err?.message || 'Failed to load dashboard');
    } finally {
      if (abortRef.current === controller) setLoading(false);
    }
  }

  React.useEffect(() => {
    load();
    return () => abortRef.current?.abort?.();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dateRange]);

  function onTypeChange(nextType) {
    const names = workoutMap?.[nextType] || [];
    const nextName = names[0] || '';
    load({ workoutType: nextType, workoutName: nextName });
  }

  function onNameChange(nextName) {
    load({ workoutType, workoutName: nextName });
  }

  const isCardio = workoutType === 'Cardio';
  const graph1Label = isCardio ? `Pace (${paceUnit})` : `Weight (${weightUnit})`;
  const graph2Label = isCardio ? `Distance (${distanceUnit})` : `Volume (${weightUnit} × sets × reps)`;

  const graph1Title = isCardio ? 'Pace' : 'Weight';
  const graph2Title = isCardio ? `Distance per run (${distanceUnit})` : 'Volume';

  const graph1AccentVar = isCardio ? '--blue' : '--teal';
  const graph2AccentVar = isCardio ? '--teal' : '--orange';

  const graph1AxisLabel = isCardio ? `Pace (${paceUnit})` : `Weight (${weightUnit})`;
  const graph2AxisLabel = isCardio ? `Distance (${distanceUnit})` : `Volume (${weightUnit})`;

  const graph1Unit = isCardio ? paceUnit : weightUnit;
  const graph1Numbers = (data?.graph1Values || []).map(Number).filter(Number.isFinite);
  const latestValue = graph1Numbers.length ? graph1Numbers[graph1Numbers.length - 1] : null;
  const firstValue = graph1Numbers.length ? graph1Numbers[0] : null;
  // Lower pace is better for cardio; higher weight is better for lifting
  const bestValue = graph1Numbers.length ? (isCardio ? Math.min(...graph1Numbers) : Math.max(...graph1Numbers)) : null;
  const change = latestValue != null && graph1Numbers.length > 1 ? latestValue - firstValue : null;
  const improved = change != null && (isCardio ? change < 0 : change > 0);
  const formatGraph1 = (v) => (v == null ? '—' : isCardio ? formatMinutesAsClock(v) : formatDisplayNumber(v));
  const formatChange = (v) => {
    const sign = v > 0 ? '+' : v < 0 ? '−' : '±';
    return `${sign}${formatGraph1(Math.abs(v))}`;
  };

  return (
    <div className="main-content">
      <div className="page">
      <header className="page-head">
        <div>
          <p className="kicker">Performance</p>
          <h2 className="page-title page-title--dashboard">Dashboard</h2>
        </div>

      <div className="dashboard-controls">
        <div className="dashboard-control">
          <div className="date-range-toggle">
            {[
              ['30', '30 days'],
              ['90', '90 days'],
              ['all', 'All time'],
            ].map(([val, label]) => (
              <button
                key={val}
                type="button"
                className={`date-range-btn ${
                  dateRange === val ? 'date-range-btn--active' : ''
                }`}
                onClick={() => setDateRange(val)}
                disabled={loading}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        <div className="dashboard-control">
          <select
            value={workoutType}
            onChange={(e) => onTypeChange(e.target.value)}
            disabled={!data || loading}
            aria-label="Workout Type"
          >
            <option value="" disabled>
              Select
            </option>

            {sortedWorkoutTypes.map(([type]) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
        </div>

        <div className="dashboard-control">
          <select
            value={workoutName}
            onChange={(e) => onNameChange(e.target.value)}
            disabled={!workoutType || loading}
            aria-label="Workout Name"
          >
            <option value="" disabled>
              Select
            </option>

            {(workoutMap?.[workoutType] || []).map((name) => (
              <option key={name} value={name}>
                {name}
              </option>
            ))}
          </select>
        </div>
      </div>
      </header>

      {error ? <div className="error-message">{error}</div> : null}

      <div className="dashboard-summary dashboard-kpi-grid">
        <div className="dashboard-kpi-card">
          <div className="dashboard-kpi-card__label">Total workouts</div>
          <div className="dashboard-kpi-card__value">{data?.totalWorkouts ?? 0}</div>
          <div className="dashboard-kpi-card__sub">All types logged</div>
        </div>
        <div className="dashboard-kpi-card">
          <div className="dashboard-kpi-card__label">Latest {graph1Title}</div>
          <div className="dashboard-kpi-card__value">
            {formatGraph1(latestValue)}
            {latestValue != null ? <small>{graph1Unit}</small> : null}
          </div>
          <div className={`dashboard-kpi-card__sub ${change == null || change === 0 ? '' : improved ? 'up' : 'down'}`}>
            {change == null ? (workoutName || 'No data yet') : `${formatChange(change)} over this range`}
          </div>
        </div>
        <div className="dashboard-kpi-card">
          <div className="dashboard-kpi-card__label">Best {graph1Title}</div>
          <div className="dashboard-kpi-card__value">
            {formatGraph1(bestValue)}
            {bestValue != null ? <small>{graph1Unit}</small> : null}
          </div>
          <div className="dashboard-kpi-card__sub">{workoutName || 'All workouts'} · {workoutType || 'All'}</div>
        </div>
        <div className="dashboard-kpi-card">
          <div className="dashboard-kpi-card__label">Latest workout</div>
          <div className="dashboard-kpi-card__value dashboard-kpi-card__value--text">{data?.latestWorkout?.workoutName || '—'}</div>
          <div className="dashboard-kpi-card__sub">{data?.latestWorkout?.workoutDate || '—'}</div>
        </div>
      </div>

      <div className="dashboard-charts">
        <LineChart
          title={graph1Title}
          labels={data?.sortedDates || []}
          values={data?.graph1Values || []}
          yLabel={graph1Label}
          yAxisLabel={graph1AxisLabel}
          accentVar={graph1AccentVar}
        />
        <LineChart
          title={graph2Title}
          labels={data?.sortedDates || []}
          values={data?.graph2Values || []}
          yLabel={graph2Label}
          yAxisLabel={graph2AxisLabel}
          accentVar={graph2AccentVar}
        />
      </div>
      {loading ? (
        <div className="loading-line">Loading dashboard…</div>
      ) : null}

      <h3 className="dashboard-section-title">Workout Details</h3>
      <div className="table-scroll">
        <table className="dashboard-table">
          <thead>
            <tr>
              <th>Date</th>
              <th>Type</th>
              <th>Name</th>
              {isCardio ? (
                <>
                  <th>Distance ({distanceUnit})</th>
                  <th>Time</th>
                </>
              ) : (
                <>
                  <th>Weight ({weightUnit})</th>
                  <th>Sets</th>
                  <th>Reps</th>
                </>
              )}
            </tr>
          </thead>
          <tbody>
            {(data?.workoutDetails || []).map((w) => (
              <tr key={w.workoutId}>
                <td>{w.workoutDate}</td>
                <td>
                  <span className={`workout-type-badge workout-type-badge--${String(w.workoutType || '').toLowerCase()}`}>
                    {w.workoutType}
                  </span>
                </td>
                <td>{w.workoutName}</td>
                {isCardio ? (
                  <>
                    <td>{formatDisplayNumber(w.distance)}</td>
                    <td>{formatMinutesAsClock(w.time)}</td>
                  </>
                ) : (
                  <>
                    <td>{formatDisplayNumber(w.weight)}</td>
                    <td>{w.sets ?? ''}</td>
                    <td>{w.reps ?? ''}</td>
                  </>
                )}
              </tr>
            ))}
            {!loading && (data?.workoutDetails || []).length === 0 ? (
              <tr><td colSpan={isCardio ? 5 : 6}>No workouts yet</td></tr>
            ) : null}
          </tbody>
        </table>
      </div>
      </div>
    </div>
  );
}
