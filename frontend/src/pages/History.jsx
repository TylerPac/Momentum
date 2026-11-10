import React from 'react';
import { Link } from 'react-router-dom';
import { apiFetch } from '../api';
import { useAuth } from '../auth';
import { formatMinutesAsClock } from '../utils/duration';
import { usePageMeta } from '../utils/pageMeta';
import { useToast } from '../utils/toast';
import { formatDisplayNumber, getUnitPreference } from '../utils/units';

const PAGE_SIZE = 50;

function InlineConfirm({ onConfirm, onCancel }) {
  return (
    <div className="inline-confirm">
      <span className="inline-confirm__label">Delete?</span>
      <button type="button" className="confirm-yes-btn" onClick={onConfirm}>Yes</button>
      <button type="button" className="confirm-no-btn" onClick={onCancel}>Cancel</button>
    </div>
  );
}

function groupByDate(items) {
  const groups = new Map();
  for (const item of items) {
    const key = item.workoutDate || 'Unknown';
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(item);
  }
  return groups;
}

export default function History() {
  const { user } = useAuth();
  const toast = useToast();
  const { distanceUnit, weightUnit } = getUnitPreference(user?.unitSystem);

  const [items, setItems] = React.useState([]);
  const [error, setError] = React.useState('');
  const [loading, setLoading] = React.useState(true);
  const [page, setPage] = React.useState(0);
  const [totalPages, setTotalPages] = React.useState(0);
  const [totalItems, setTotalItems] = React.useState(0);
  const [pendingDelete, setPendingDelete] = React.useState(null);

  const [search, setSearch] = React.useState('');
  const [workoutType, setWorkoutType] = React.useState('');
  const [dateFrom, setDateFrom] = React.useState('');
  const [dateTo, setDateTo] = React.useState('');

  usePageMeta({ title: 'Momentum — Workout History', description: 'View and manage your workout history.' });

  const abortRef = React.useRef(null);

  const load = React.useCallback(async (pageNum = 0) => {
    setError('');
    setLoading(true);
    abortRef.current?.abort?.();
    const controller = new AbortController();
    abortRef.current = controller;
    try {
      const params = new URLSearchParams({ page: pageNum, size: PAGE_SIZE });
      if (search.trim()) params.set('search', search.trim());
      if (workoutType) params.set('workoutType', workoutType);
      if (dateFrom) params.set('dateFrom', dateFrom);
      if (dateTo) params.set('dateTo', dateTo);

      const result = await apiFetch(`/workouts/history?${params}`, { signal: controller.signal });
      setItems(result?.items || []);
      setPage(result?.page ?? 0);
      setTotalPages(result?.totalPages ?? 0);
      setTotalItems(result?.totalItems ?? 0);
    } catch (err) {
      if (err?.name === 'AbortError') return;
      setError(err?.message || 'Failed to load history');
    } finally {
      if (abortRef.current === controller) setLoading(false);
    }
  }, [search, workoutType, dateFrom, dateTo]);

  React.useEffect(() => {
    load(0);
    return () => abortRef.current?.abort?.();
  }, [load]);

  async function onDeleteConfirm(id) {
    setPendingDelete(null);
    try {
      await apiFetch(`/workouts/${id}`, { method: 'DELETE' });
      toast('Workout deleted', 'success');
      load(page);
    } catch (err) {
      toast(err?.message || 'Delete failed', 'error');
    }
  }

  const grouped = groupByDate(items);
  const sortedDates = Array.from(grouped.keys()).sort((a, b) => (a > b ? -1 : 1));

  return (
    <div className="main-content">
      <div className="page">
        <header className="page-head">
          <div>
            <p className="kicker">Training log</p>
            <h2>Workout History</h2>
          </div>
        </header>
        {error ? <div className="error-message">{error}</div> : null}

        <div className="history-filters">
          <label>
            Search
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Name or type…"
            />
          </label>
          <label>
            Type
            <select value={workoutType} onChange={(e) => setWorkoutType(e.target.value)}>
              <option value="">All types</option>
              <option value="Cardio">Cardio</option>
              <option value="Weightlifting">Weightlifting</option>
            </select>
          </label>
          <label>
            From
            <input type="date" value={dateFrom} onChange={(e) => setDateFrom(e.target.value)} />
          </label>
          <label>
            To
            <input type="date" value={dateTo} onChange={(e) => setDateTo(e.target.value)} />
          </label>
        </div>

        {loading ? <div className="loading-line">Loading…</div> : null}

        {!loading && items.length === 0 ? (
          <div className="empty-state">
            <div className="empty-state__title">No workouts found</div>
            <div className="empty-state__sub">
              <Link to="/workouts/new" className="edit-btn">Log your first workout →</Link>
            </div>
          </div>
        ) : (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Type</th>
                  <th>Name</th>
                  <th>Distance ({distanceUnit})</th>
                  <th>Time</th>
                  <th>Weight ({weightUnit})</th>
                  <th>Sets</th>
                  <th>Reps</th>
                  <th>Notes</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {sortedDates.map((date) => (
                  <React.Fragment key={date}>
                    <tr className="history-date-group">
                      <th colSpan={9}>{date}</th>
                    </tr>
                    {grouped.get(date).map((w) => (
                      <tr key={w.workoutId}>
                        <td>
                          <span className={`workout-type-badge workout-type-badge--${String(w.workoutType || '').toLowerCase()}`}>
                            {w.workoutType}
                          </span>
                        </td>
                        <td>{w.workoutName}</td>
                        <td>{formatDisplayNumber(w.distance)}</td>
                        <td>{w.workoutType === 'Cardio' ? formatMinutesAsClock(w.time) : (w.time ?? '')}</td>
                        <td>{formatDisplayNumber(w.weight)}</td>
                        <td>{w.sets ?? ''}</td>
                        <td>{w.reps ?? ''}</td>
                        <td className="notes-cell">{w.notes || ''}</td>
                        <td>
                          {pendingDelete === w.workoutId ? (
                            <InlineConfirm
                              onConfirm={() => onDeleteConfirm(w.workoutId)}
                              onCancel={() => setPendingDelete(null)}
                            />
                          ) : (
                            <div className="action-buttons">
                              <Link className="edit-btn" to={`/workouts/${w.workoutId}/edit`}>Edit</Link>
                              <button
                                className="delete-btn"
                                type="button"
                                onClick={() => setPendingDelete(w.workoutId)}
                              >Delete</button>
                            </div>
                          )}
                        </td>
                      </tr>
                    ))}
                  </React.Fragment>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {totalPages > 1 && (
          <div className="pagination">
            <button
              type="button"
              className="pagination__btn"
              onClick={() => load(page - 1)}
              disabled={page === 0 || loading}
            >← Prev</button>
            <span className="pagination__info">Page {page + 1} of {totalPages} · {totalItems} total</span>
            <button
              type="button"
              className="pagination__btn"
              onClick={() => load(page + 1)}
              disabled={page >= totalPages - 1 || loading}
            >Next →</button>
          </div>
        )}
      </div>
    </div>
  );
}

