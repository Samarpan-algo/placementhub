import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useApi } from '../../api/useApi.js';
import EmptyState from '../../components/EmptyState.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import Loading from '../../components/Loading.jsx';
import Pagination from '../../components/Pagination.jsx';
import StatusBadge from '../../components/StatusBadge.jsx';
import WithdrawButton from '../../components/WithdrawButton.jsx';
import { formatCtc, formatDateTime } from '../../utils/format.js';

const PAGE_SIZE = 10;

/** The current student's applications, newest first, with Withdraw where it is still allowed. */
export default function MyApplicationsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = Math.max(Number.parseInt(searchParams.get('page') ?? '1', 10) || 1, 1) - 1;
  const { data, loading, error, reload } = useApi(`/api/students/me/applications?page=${page}&size=${PAGE_SIZE}`);

  // The result of the last withdrawal (success message or error), shown above the table
  const [actionError, setActionError] = useState(null);
  const [message, setMessage] = useState(null);

  function handleWithdrawDone({ message: doneMessage, error: doneError }) {
    setMessage(doneMessage ?? null);
    // e.g. 409 "Withdrawal closed on ..." if the deadline passed while the page was open
    setActionError(doneError ?? null);
    reload(); // show the real statuses now, whatever happened
  }

  function goToPage(next) {
    setSearchParams(next > 0 ? { page: String(next + 1) } : {});
  }

  return (
    <div>
      <div className="page-heading">
        <h1>My applications</h1>
      </div>

      {message && <div className="alert alert-success">{message}</div>}
      <ErrorAlert error={actionError} />

      {error && <ErrorAlert error={error} onRetry={reload} />}
      {!error && loading && !data && <Loading text="Loading your applications…" />}

      {data && data.totalElements === 0 && (
        <EmptyState title="You haven't applied to any drive yet.">
          <Link to="/student/drives">Browse open drives</Link>
        </EmptyState>
      )}
      {data && data.totalElements > 0 && data.content.length === 0 && (
        <EmptyState title="There are no applications on this page.">
          <button type="button" className="btn btn-secondary" onClick={() => goToPage(0)}>
            Go to page 1
          </button>
        </EmptyState>
      )}

      {data && data.content.length > 0 && (
        <>
          {/* table-wrap scrolls sideways on narrow screens instead of squeezing the columns */}
          <div className="table-wrap card">
            <table className="table">
              <thead>
                <tr>
                  <th>Drive</th>
                  <th>CTC</th>
                  <th>Status</th>
                  <th>Applied</th>
                  <th aria-label="Actions" />
                </tr>
              </thead>
              <tbody>
                {data.content.map((application) => (
                  <tr key={application.id}>
                    <td>
                      <Link to={`/student/applications/${application.id}`}>{application.roleTitle}</Link>
                      <div className="muted">{application.companyName}</div>
                    </td>
                    <td>{formatCtc(application.ctcLpa)}</td>
                    <td>
                      <StatusBadge status={application.status} />
                      {application.statusReason && <div className="status-reason">{application.statusReason}</div>}
                    </td>
                    <td>{formatDateTime(application.appliedAt)}</td>
                    <td className="actions">
                      <WithdrawButton
                        application={application}
                        onDone={handleWithdrawDone}
                        disabled={loading}
                        className="btn btn-secondary btn-small"
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination page={data.page} totalPages={data.totalPages} disabled={loading} onChange={goToPage} />
        </>
      )}
    </div>
  );
}
