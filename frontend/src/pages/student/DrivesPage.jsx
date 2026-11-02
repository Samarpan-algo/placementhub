import { Link, useSearchParams } from 'react-router';
import { useApi } from '../../api/useApi.js';
import Eligibility from '../../components/Eligibility.jsx';
import EmptyState from '../../components/EmptyState.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import Loading from '../../components/Loading.jsx';
import Pagination from '../../components/Pagination.jsx';
import StatusBadge from '../../components/StatusBadge.jsx';
import { formatCtc, formatDateTime, formatJobType } from '../../utils/format.js';

const PAGE_SIZE = 10;

function DriveCard({ item }) {
  const { drive, eligible, reasons, applicationStatus } = item;
  return (
    <li className="card drive-card">
      <div className="drive-card-top">
        <div>
          <h2 className="drive-title">
            <Link to={`/student/drives/${drive.id}`}>{drive.roleTitle}</Link>
          </h2>
          <p className="muted">
            {drive.companyName} · {formatJobType(drive.jobType)} · {drive.location || 'Location not given'}
          </p>
        </div>
        {applicationStatus && (
          <div className="applied-status">
            <span className="muted">You applied:</span> <StatusBadge status={applicationStatus} />
          </div>
        )}
      </div>
      <dl className="facts">
        <div>
          <dt>CTC</dt>
          <dd>{formatCtc(drive.ctcLpa)}</dd>
        </div>
        <div>
          <dt>Apply by</dt>
          <dd>{formatDateTime(drive.registrationDeadline)}</dd>
        </div>
      </dl>
      <Eligibility eligible={eligible} reasons={reasons} />
    </li>
  );
}

/**
 * OPEN drives, soonest deadline first. The page number and the "Eligible only" toggle live in the URL
 * (?page=2&eligibleOnly=true), so Back, Refresh and shared links all keep them.
 */
export default function DrivesPage() {
  // useSearchParams = useState for the URL's ?query part: reading it gives the current values,
  // setting it changes the URL (and re-renders this page with the new values)
  const [searchParams, setSearchParams] = useSearchParams();
  const eligibleOnly = searchParams.get('eligibleOnly') === 'true';
  // In the URL pages start at 1 (friendlier); the API counts from 0
  const page = Math.max(Number.parseInt(searchParams.get('page') ?? '1', 10) || 1, 1) - 1;

  // A new URL here (other page or toggle) makes useApi load again
  const { data, loading, error, reload } = useApi(
    `/api/students/me/drives?eligibleOnly=${eligibleOnly}&page=${page}&size=${PAGE_SIZE}`,
  );

  function changeParams(nextEligibleOnly, nextPage) {
    const params = {};
    if (nextEligibleOnly) params.eligibleOnly = 'true';
    if (nextPage > 0) params.page = String(nextPage + 1);
    setSearchParams(params);
  }

  return (
    <div>
      <div className="page-heading">
        <h1>Open drives</h1>
        <label className="toggle">
          <input
            type="checkbox"
            checked={eligibleOnly}
            disabled={loading}
            // Going back to page 1: page 3 of "all drives" may not exist among the eligible ones
            onChange={(e) => changeParams(e.target.checked, 0)}
          />
          Eligible only
        </label>
      </div>

      {error && <ErrorAlert error={error} onRetry={reload} />}
      {!error && loading && !data && <Loading text="Loading drives…" />}

      {data && data.content.length === 0 && (
        <EmptyState
          title={
            data.totalElements > 0
              ? 'There are no drives on this page.'
              : eligibleOnly
                ? 'No open drive you are eligible for right now.'
                : 'No drives are open right now.'
          }
        >
          {data.totalElements > 0 ? (
            <button type="button" className="btn btn-secondary" onClick={() => changeParams(eligibleOnly, 0)}>
              Go to page 1
            </button>
          ) : (
            eligibleOnly && 'Untick "Eligible only" to see every open drive and why you are not eligible.'
          )}
        </EmptyState>
      )}

      {data && data.content.length > 0 && (
        <>
          <p className="muted">
            {data.totalElements} drive{data.totalElements === 1 ? '' : 's'}
            {eligibleOnly ? ' you are eligible for' : ' open'}.
          </p>
          <ul className="drive-list">
            {data.content.map((item) => (
              <DriveCard key={item.drive.id} item={item} />
            ))}
          </ul>
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            disabled={loading}
            onChange={(next) => changeParams(eligibleOnly, next)}
          />
        </>
      )}
    </div>
  );
}
