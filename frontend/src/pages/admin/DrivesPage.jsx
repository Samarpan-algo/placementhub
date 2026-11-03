import { Link, useSearchParams } from 'react-router';
import { useApi } from '../../api/useApi.js';
import EmptyState from '../../components/EmptyState.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import Loading from '../../components/Loading.jsx';
import Pagination from '../../components/Pagination.jsx';
import StatusBadge from '../../components/StatusBadge.jsx';
import { DRIVE_STATUSES } from '../../utils/drives.js';
import { formatCtc, formatDateTime, formatJobType } from '../../utils/format.js';

const PAGE_SIZE = 10;

/**
 * Every drive, soonest deadline first, with the two filters GET /api/drives supports: status and company.
 * Filters and page live in the URL (?status=OPEN&companyId=2&page=2), like the student lists.
 */
export default function DrivesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const status = searchParams.get('status') ?? '';
  const companyId = searchParams.get('companyId') ?? '';
  const page = Math.max(Number.parseInt(searchParams.get('page') ?? '1', 10) || 1, 1) - 1;

  // URLSearchParams builds "status=OPEN&page=0&size=10", leaving out the filters that aren't set
  const query = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
  if (status) query.set('status', status);
  if (companyId) query.set('companyId', companyId);
  const { data, loading, error, reload } = useApi(`/api/drives?${query}`);

  // Options for the company filter (the backend caps a page at 100: a known limitation)
  const companies = useApi('/api/companies?size=100');

  function changeParams(next) {
    const merged = { status, companyId, page: 0, ...next };
    const params = {};
    if (merged.status) params.status = merged.status;
    if (merged.companyId) params.companyId = merged.companyId;
    if (merged.page > 0) params.page = String(merged.page + 1);
    setSearchParams(params);
  }

  const filtered = Boolean(status || companyId);

  return (
    <div>
      <div className="page-heading">
        <h1>Drives</h1>
        <Link to="/admin/drives/new" className="btn btn-primary">
          New drive
        </Link>
      </div>

      <div className="filter-bar">
        <div className="field">
          <label htmlFor="statusFilter">Status</label>
          {/* A new filter goes back to page 1: page 3 of all drives may not exist among the OPEN ones */}
          <select id="statusFilter" value={status} onChange={(e) => changeParams({ status: e.target.value })}>
            <option value="">All statuses</option>
            {DRIVE_STATUSES.map((s) => (
              <option key={s} value={s}>
                {s.charAt(0) + s.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="companyFilter">Company</label>
          <select
            id="companyFilter"
            value={companyId}
            disabled={!companies.data}
            onChange={(e) => changeParams({ companyId: e.target.value })}
          >
            <option value="">All companies</option>
            {companies.data?.content.map((company) => (
              <option key={company.id} value={company.id}>
                {company.name}
              </option>
            ))}
          </select>
          {companies.error && <span className="field-error">Could not load companies: {companies.error.message}</span>}
        </div>
        {filtered && (
          <button type="button" className="btn btn-secondary" onClick={() => setSearchParams({})}>
            Clear filters
          </button>
        )}
      </div>

      {error && <ErrorAlert error={error} onRetry={reload} />}
      {!error && loading && !data && <Loading text="Loading drives…" />}

      {data && data.content.length === 0 && (
        <EmptyState
          title={
            data.totalElements > 0
              ? 'There are no drives on this page.'
              : filtered
                ? 'No drives match these filters.'
                : 'No drives yet.'
          }
        >
          {data.totalElements > 0 ? (
            <button type="button" className="btn btn-secondary" onClick={() => changeParams({ page: 0 })}>
              Go to page 1
            </button>
          ) : (
            !filtered && 'Create the first one with "New drive".'
          )}
        </EmptyState>
      )}

      {data && data.content.length > 0 && (
        <>
          <p className="muted">
            {data.totalElements} drive{data.totalElements === 1 ? '' : 's'}
            {filtered ? ' match these filters' : ''}.
          </p>
          <div className="table-wrap card">
            <table className="table">
              <thead>
                <tr>
                  <th>Role</th>
                  <th>Company</th>
                  <th>CTC</th>
                  <th>Registration deadline</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((drive) => (
                  <tr key={drive.id}>
                    <td>
                      <Link to={`/admin/drives/${drive.id}`}>
                        <strong>{drive.roleTitle}</strong>
                      </Link>
                      <div className="muted">{formatJobType(drive.jobType)}</div>
                    </td>
                    <td>{drive.companyName}</td>
                    <td>{formatCtc(drive.ctcLpa)}</td>
                    <td>{formatDateTime(drive.registrationDeadline)}</td>
                    <td>
                      <StatusBadge status={drive.status} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            disabled={loading}
            onChange={(next) => changeParams({ page: next })}
          />
        </>
      )}
    </div>
  );
}
