import { useState } from 'react';
import api from '../../api/client.js';
import { fieldErrorsFrom, toApiError } from '../../api/errors.js';
import { useApi } from '../../api/useApi.js';
import ConfirmDialog from '../../components/ConfirmDialog.jsx';
import EmptyState from '../../components/EmptyState.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import Field from '../../components/Field.jsx';
import Loading from '../../components/Loading.jsx';
import { formatCtc, formatDateTime } from '../../utils/format.js';

const FIELDS = ['active', 'ctcThresholdLpa', 'maxOffers'];

// What each type means, and which one setting it has (PolicyService.checkSettings): sending any other is a 400
const POLICY_INFO = {
  ONE_OFFER: {
    title: 'One offer',
    description: 'A student with a full-time offer cannot apply to or be selected for another full-time drive.',
  },
  DREAM_OFFER: {
    title: 'Dream offer',
    description:
      'A student with one full-time offer below the threshold gets one upgrade, to a drive at or above the threshold.',
    setting: 'ctcThresholdLpa',
  },
  MAX_OFFERS: {
    title: 'Maximum offers',
    description: 'A student who already holds this many full-time offers cannot get another.',
    setting: 'maxOffers',
  },
};

/** The same rules as PolicyUpdateRequest (the backend checks again). Returns the errors for one form. */
function validate(setting, threshold, maxOffers) {
  const errors = {};
  if (setting === 'ctcThresholdLpa' && !(/^\d{1,4}(\.\d{1,2})?$/.test(threshold) && Number(threshold) > 0)) {
    errors.ctcThresholdLpa = 'Above 0, at most 9999.99, with at most 2 decimals';
  }
  if (setting === 'maxOffers' && !(/^\d+$/.test(maxOffers) && Number(maxOffers) >= 1 && Number(maxOffers) <= 20)) {
    errors.maxOffers = 'A whole number, 1 to 20';
  }
  return errors;
}

function activeLabel(active) {
  return active ? 'Active' : 'Inactive';
}

/**
 * One policy's form. The page gives it key={type + updatedAt}, so after a save the fresh values from the
 * backend replace the form; after a failed save, nothing changed on the backend and the typed values stay.
 */
function PolicyCard({ policy, onSaved, onFailed }) {
  const info = POLICY_INFO[policy.type];
  const savedThreshold = policy.ctcThresholdLpa == null ? '' : Number(policy.ctcThresholdLpa).toFixed(2);
  const savedMaxOffers = policy.maxOffers == null ? '' : String(policy.maxOffers);

  const [active, setActive] = useState(policy.active);
  const [threshold, setThreshold] = useState(savedThreshold);
  const [maxOffers, setMaxOffers] = useState(savedMaxOffers);
  const [fieldErrors, setFieldErrors] = useState({});
  const [error, setError] = useState(null);
  const [confirming, setConfirming] = useState(false);
  const [saving, setSaving] = useState(false);

  const thresholdChanged = info.setting === 'ctcThresholdLpa' && Number(threshold) !== Number(savedThreshold);
  const maxOffersChanged = info.setting === 'maxOffers' && Number(maxOffers) !== Number(savedMaxOffers);
  const changed = active !== policy.active || thresholdChanged || maxOffersChanged;

  function handleSubmit(event) {
    event.preventDefault();
    const errors = validate(info.setting, threshold.trim(), maxOffers.trim());
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      setError({ message: 'Please fix the highlighted fields.', details: [] });
      return;
    }
    setError(null);
    setConfirming(true);
  }

  async function confirmSave() {
    setSaving(true);
    setError(null);
    // Only this type's own setting: the backend rejects one that doesn't apply
    const body = { active };
    if (info.setting === 'ctcThresholdLpa') body.ctcThresholdLpa = Number(threshold);
    if (info.setting === 'maxOffers') body.maxOffers = Number(maxOffers);
    try {
      await api.put(`/api/policies/${policy.type}`, body);
      onSaved(info.title);
    } catch (err) {
      // 400: messages go under their fields; 409 (the ONE/DREAM conflict) and anything else: the top alert
      const split = fieldErrorsFrom(toApiError(err), FIELDS);
      setFieldErrors(split.fieldErrors);
      setError(split.error);
      setSaving(false);
      setConfirming(false);
      onFailed();
    }
  }

  return (
    <section className="card">
      <div className="page-heading">
        <div>
          <h2>{info.title}</h2>
          <p className="muted">{policy.type}</p>
        </div>
        <span className={`badge ${policy.active ? 'badge-selected' : 'badge-closed'}`}>{activeLabel(policy.active)}</span>
      </div>
      <p>{info.description}</p>
      <form className="form" onSubmit={handleSubmit} noValidate>
        <ErrorAlert error={error} />
        <label className="toggle">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
          Active
        </label>
        {fieldErrors.active && <span className="field-error">{fieldErrors.active}</span>}

        {info.setting === 'ctcThresholdLpa' && (
          <Field
            id="ctcThresholdLpa"
            label="CTC threshold (LPA)"
            error={fieldErrors.ctcThresholdLpa}
            hint="Kept even while the policy is inactive."
          >
            <input
              id="ctcThresholdLpa"
              inputMode="decimal"
              value={threshold}
              onChange={(e) => setThreshold(e.target.value)}
            />
          </Field>
        )}
        {info.setting === 'maxOffers' && (
          <Field
            id="maxOffers"
            label="Maximum offers"
            error={fieldErrors.maxOffers}
            hint="Kept even while the policy is inactive."
          >
            <input id="maxOffers" inputMode="numeric" value={maxOffers} onChange={(e) => setMaxOffers(e.target.value)} />
          </Field>
        )}

        <div className="button-row">
          <button type="submit" className="btn btn-primary" disabled={!changed || saving}>
            {saving ? 'Saving…' : 'Save'}
          </button>
        </div>
      </form>
      <p className="hint">Last updated {formatDateTime(policy.updatedAt)}</p>

      <ConfirmDialog
        open={confirming}
        title={`Change ${info.title}?`}
        confirmLabel="Save policy"
        busyLabel="Saving…"
        busy={saving}
        onConfirm={confirmSave}
        onCancel={() => setConfirming(false)}
      >
        <ul>
          {active !== policy.active && (
            <li>
              {activeLabel(policy.active)} → {activeLabel(active)}
            </li>
          )}
          {thresholdChanged && (
            <li>
              Threshold {formatCtc(savedThreshold)} → {formatCtc(threshold)}
            </li>
          )}
          {maxOffersChanged && (
            <li>
              Maximum offers {savedMaxOffers} → {Number(maxOffers)}
            </li>
          )}
        </ul>
        <p>
          This takes effect from the next application or selection, for every full-time drive. Existing applications
          and offers are not changed now.
        </p>
        <p>The change is recorded in the audit log.</p>
      </ConfirmDialog>
    </section>
  );
}

/** The three placement policies, each saved on its own with PUT /api/policies/{type}. */
export default function PoliciesPage() {
  const { data, loading, error, reload } = useApi('/api/policies');
  const [message, setMessage] = useState(null);

  function handleSaved(title) {
    setMessage(`Saved the ${title} policy.`);
    reload(); // show the settings as the backend has them now
  }

  return (
    <div className="stack">
      <div>
        <h1>Placement policies</h1>
        <p className="muted">
          These rules are checked whenever a student applies to a full-time drive and when you select them. Internships
          are never blocked.
        </p>
        <p className="hint">
          One offer and Dream offer contradict each other, so only one can be active. Switching between them takes two
          saves: first deactivate one, then activate the other.
        </p>
      </div>

      {message && <div className="alert alert-success">{message}</div>}
      {error && <ErrorAlert error={error} onRetry={reload} />}
      {!error && loading && !data && <Loading text="Loading policies…" />}
      {data && data.length === 0 && <EmptyState title="No placement policies found." />}

      {data &&
        data.map((policy) => (
          <PolicyCard
            key={`${policy.type}-${policy.updatedAt}`}
            policy={policy}
            onSaved={handleSaved}
            onFailed={() => {
              setMessage(null);
              reload();
            }}
          />
        ))}
    </div>
  );
}
