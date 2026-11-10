import React from 'react';
import { useNavigate } from 'react-router-dom';
import { apiFetch } from '../api';
import { useAuth } from '../auth';
import { parsePartsToMinutes, splitMinutesToParts } from '../utils/duration';
import { usePageMeta } from '../utils/pageMeta';
import { useToast } from '../utils/toast';
import { formatDisplayNumber, getUnitPreference } from '../utils/units';

function makeEmptySet() {
  return { id: Date.now() + Math.random(), weight: '', sets: '', reps: '' };
}

export default function AddWorkout() {
  const navigate = useNavigate();
  const toast = useToast();
  const { user } = useAuth();
  const { weightUnit, distanceUnit } = getUnitPreference(user?.unitSystem);

  usePageMeta({ title: 'Momentum — Add Workout', description: 'Add a new cardio or weightlifting workout.' });

  const [workoutType, setWorkoutType] = React.useState('Cardio');
  const [workoutName, setWorkoutName] = React.useState('');
  const [workoutDate, setWorkoutDate] = React.useState('');
  const [notes, setNotes] = React.useState('');

  // Cardio fields
  const [distance, setDistance] = React.useState('');
  const [timeMinutes, setTimeMinutes] = React.useState('');
  const [timeSeconds, setTimeSeconds] = React.useState('');

  // Weightlifting: multiple sets
  const [setRows, setSetRows] = React.useState([makeEmptySet()]);

  const [existingNames, setExistingNames] = React.useState([]);
  const [error, setError] = React.useState('');
  const [namesLoading, setNamesLoading] = React.useState(true);
  const abortRef = React.useRef(null);

  React.useEffect(() => {
    setNamesLoading(true);
    abortRef.current?.abort?.();
    const controller = new AbortController();
    abortRef.current = controller;

    apiFetch('/workouts/names', { signal: controller.signal })
      .then((names) => setExistingNames(names || []))
      .catch(() => setExistingNames([]))
      .finally(() => {
        if (abortRef.current === controller) setNamesLoading(false);
      });

    return () => abortRef.current?.abort?.();
  }, []);

  // Auto-fill last session when a known name is entered
  React.useEffect(() => {
    if (!workoutName || !existingNames.includes(workoutName)) {
      return;
    }
    const controller = new AbortController();
    apiFetch(`/workouts/last?name=${encodeURIComponent(workoutName)}`, { signal: controller.signal })
      .then((last) => {
        if (!last) return;
        setWorkoutType(last.workoutType || 'Cardio');
        if (last.workoutType === 'Cardio') {
          setDistance(formatDisplayNumber(last.distance));
          const parts = splitMinutesToParts(last.time);
          setTimeMinutes(parts.minutes);
          setTimeSeconds(parts.seconds);
        } else {
          setSetRows([{ id: Date.now(), weight: formatDisplayNumber(last.weight), sets: last.sets ?? '', reps: last.reps ?? '' }]);
        }
        setNotes('');
      })
      .catch(() => {});
    return () => controller.abort();
  }, [workoutName, existingNames]);

  function updateSetRow(id, field, value) {
    setSetRows((rows) => rows.map((r) => r.id === id ? { ...r, [field]: value } : r));
  }

  function addSetRow() {
    setSetRows((rows) => {
      const last = rows[rows.length - 1];
      return [...rows, { id: Date.now() + Math.random(), weight: last?.weight ?? '', sets: '', reps: last?.reps ?? '' }];
    });
  }

  function removeSetRow(id) {
    setSetRows((rows) => rows.length > 1 ? rows.filter((r) => r.id !== id) : rows);
  }

  async function onSubmit(e) {
    e.preventDefault();
    setError('');

    try {
      if (workoutType === 'Weightlifting') {
        const payloads = setRows.map((row) => ({
          workoutType,
          workoutName,
          workoutDate,
          distance: null,
          time: null,
          weight: row.weight !== '' ? Number(row.weight) : null,
          sets: row.sets !== '' ? Number(row.sets) : null,
          reps: row.reps !== '' ? Number(row.reps) : null,
          notes: notes || null,
        }));
        await apiFetch('/workouts/batch', { method: 'POST', body: JSON.stringify(payloads) });
      } else {
        const parsedTime = parsePartsToMinutes(timeMinutes, timeSeconds);
        const payload = {
          workoutType,
          workoutName,
          workoutDate,
          distance: distance !== '' ? Number(distance) : null,
          time: parsedTime,
          weight: null,
          sets: null,
          reps: null,
          notes: notes || null,
        };
        await apiFetch('/workouts', { method: 'POST', body: JSON.stringify(payload) });
      }
      toast('Workout added!', 'success');
      navigate('/history');
    } catch (err) {
      setError(err?.message || 'Failed to add workout');
    }
  }

  const isCardio = workoutType === 'Cardio';

  return (
    <div className="main-content">
      <div className="page page-narrow">
        <header className="page-head">
          <div>
            <p className="kicker">Log a session</p>
            <h2>Add a New Workout</h2>
          </div>
        </header>
        {error ? <div className="error-message">{error}</div> : null}
        {namesLoading ? <div className="loading-line">Loading workout names…</div> : null}

        <form onSubmit={onSubmit} className="workout-form">
          <label>
            Workout Type
            <select value={workoutType} onChange={(e) => setWorkoutType(e.target.value)}>
              <option value="Cardio">Cardio</option>
              <option value="Weightlifting">Weightlifting</option>
            </select>
          </label>

          <label>
            Workout Name
            <input list="workout-names" value={workoutName} onChange={(e) => setWorkoutName(e.target.value)} required />
            <datalist id="workout-names">
              {existingNames.map((n) => (
                <option key={n} value={n} />
              ))}
            </datalist>
          </label>

          <label>
            Workout Date
            <input type="date" value={workoutDate} onChange={(e) => setWorkoutDate(e.target.value)} required />
          </label>

          {isCardio ? (
            <>
              <label>
                Distance ({distanceUnit})
                <input value={distance} onChange={(e) => setDistance(e.target.value)} inputMode="decimal" />
              </label>
              <label>
                Time
                <div className="cardio-time-grid">
                  <input
                    value={timeMinutes}
                    onChange={(e) => setTimeMinutes(e.target.value.replace(/\D/g, ''))}
                    placeholder="Min"
                    inputMode="numeric"
                    autoComplete="off"
                    aria-label="Cardio time minutes"
                  />
                  <input
                    value={timeSeconds}
                    onChange={(e) => setTimeSeconds(e.target.value.replace(/\D/g, ''))}
                    placeholder="Sec"
                    inputMode="numeric"
                    autoComplete="off"
                    aria-label="Cardio time seconds"
                  />
                </div>
              </label>
            </>
          ) : (
            <>
              <div className="form-section-label">Sets</div>
              <div className="set-list">
                {setRows.map((row, i) => (
                  <div key={row.id} className="set-row">
                    <span className="set-row__num">{i + 1}</span>
                    <input
                      value={row.weight}
                      onChange={(e) => updateSetRow(row.id, 'weight', e.target.value)}
                      inputMode="decimal"
                      placeholder={`Wt (${weightUnit})`}
                      aria-label={`Set ${i + 1} weight`}
                    />
                    <input
                      value={row.sets}
                      onChange={(e) => updateSetRow(row.id, 'sets', e.target.value)}
                      inputMode="numeric"
                      placeholder="Sets"
                      aria-label={`Set ${i + 1} sets`}
                    />
                    <input
                      value={row.reps}
                      onChange={(e) => updateSetRow(row.id, 'reps', e.target.value)}
                      inputMode="numeric"
                      placeholder="Reps"
                      aria-label={`Set ${i + 1} reps`}
                    />
                    <button
                      type="button"
                      className="set-row__remove"
                      onClick={() => removeSetRow(row.id)}
                      aria-label={`Remove set ${i + 1}`}
                      disabled={setRows.length === 1}
                    >×</button>
                  </div>
                ))}
              </div>
              <button type="button" className="btn-secondary" onClick={addSetRow}>+ Add Set</button>
            </>
          )}

          <label>
            Notes (optional)
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="How did it feel? Any injuries or PRs?"
              maxLength={1000}
            />
          </label>

          <button type="submit" className="btn-primary">Add Workout</button>
        </form>
      </div>
    </div>
  );
}

