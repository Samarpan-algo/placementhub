import { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';
import { downloadFile } from '../../api/download.js';
import { openResume } from '../../api/resume.js';
import { useApi } from '../../api/useApi.js';
import EmptyState from '../../components/EmptyState.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import Loading from '../../components/Loading.jsx';
import Pagination from '../../components/Pagination.jsx';
import StatusBadge from '../../components/StatusBadge.jsx';
import { BRANCHES } from '../../utils/branches.js';
import { formatDateTime } from '../../utils/format.js';

// The backend's default page size for this list
const PAGE_SIZE = 20;
// Must match the backend's ApplicationStatus enum
const APPLICATION_STATUSES = ['APPLIED', 'SHORTLISTED', 'SELECTED', 'REJECTED', 'WITHDRAWN', 'INELIGIBLE'];

/** The same rule as ApplicantService.checkMinCgpa: 0 to 10, at most 2 decimals ("7", "7.5", "7.50"). */
function isValidCgpa(value) {
  return /^\d+(\.\d{1,2})?$/.test(value) && Number(value) <= 10;
}

/**
 * The minimum CGPA box. It is applied with the button (or Enter), not on every keystroke, so "7." isn't
 * sent half-typed. The page gives it key={current URL value}, so "Clear filters" resets what's typed.
 */
function MinCgpaFilter({ value, onApply }) {
  const [text, setText] = useState(value);
  const [error, setError] = useState(null);

  function handleSubmit(event) {
    event.preventDefault();
    const trimmed = text.trim();
    if (trimmed && !isValidCgpa(trimmed)) {
      setError('0 to 10, at most 2 decimals');
      return;
    }
    setError(null);
    onApply(trimmed);
  }

  return (
    <form className="field min-cgpa-filter" onSubmit={handleSubmit} noValidate>
      <label htmlFor="minCgpaFilter">Minimum CGPA</label>
      <div className="inline-input">
        <input
          id="minCgpaFilter"
          inputMode="decimal"
          placeholder="e.g. 7.5"
          value={text}
          onChange={(e) => {
            setText(e.target.value);
            setError(null);
          }}
        />
        <button type="submit" className="btn btn-secondary">
          Apply
        </button>
      </div>
      {error && <span className="field-error">{error}</span>}
    </form>
  );
}

/** "Round 2 of 3 (Technical): Cleared", or where the applicant stands when there's no result yet. */
function Progress({ progress }) {
  const { totalRounds, latestRound } = progress;
  if (totalRounds === 0) return <span className="muted">No rounds yet</span>;
  if (!latestRound) {
    return (
      <span className="muted">
        No results yet ({totalRounds} round{totalRounds === 1 ? '' : 's'})
      </span>
    );
  }
  return (
    <div>
      <div>
        Round {latestRound.roundNo} of {totalRounds} ({latestRound.name})
      </div>
      <StatusBadge status={latestRound.result} />
      {latestRound.remarks && <div className="status-reason">{latestRound.remarks}</div>}
    </div>
  );
}

/**
 * One drive's applicants, sorted by roll number, with the filters the backend supports: status, branch
 * and minimum CGPA. Filters and page live in the URL. "Export CSV" downloads every row that matches the
 * same filters (not just this page).
 */
export default function ApplicantsPage() {
  const { id } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const status = searchParams.get('status') ?? '';
  const branch = searchParams.get('branch') ?? '';
  const minCgpa = searchParams.get('minCgpa') ?? '';
  const page = Math.max(Number.parseInt(searchParams.get('page') ?? '1', 10) || 1, 1) - 1;

  // The filters as query parameters, shared by the list and the export
  const filters = new URLSearchParams();
  if (status) filters.set('status', status);
  if (branch) filters.set('branch', branch);
  if (minCgpa) filters.set('minCgpa', minCgpa);
  const listQuery = new URLSearchParams(filters);
  listQuery.set('page', String(page));
  listQuery.set('size', String(PAGE_SIZE));

  const drive = useApi(`/api/drives/${id}`);
  const { data, loading, error, reload } = useApi(`/api/drives/${id}/applications?${listQuery}`);

  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState(null);
  const [openingResumeOf, setOpeningResumeOf] = useState(null);
  const [resumeError, setResumeError] = useState(null);

  function changeParams(next) {
    const merged = { status, branch, minCgpa, page: 0, ...next };
    const params = {};
    if (merged.status) params.status = merged.status;
    if (merged.branch) params.branch = merged.branch;
    if (merged.minCgpa) params.minCgpa = merged.minCgpa;
    if (merged.page > 0) params.page = String(merged.page + 1);
    setSearchParams(params);
  }

  async function handleViewResume(studentId) {
    setResumeError(null);
    setOpeningResumeOf(studentId);
    try {
      await openResume(`/api/students/${studentId}/resume`);
    } catch (apiError) {
      setResumeError(apiError);
    } finally {
      setOpeningResumeOf(null);
    }
  }

  async function handleExport() {
    setExporting(true);
    setExportError(null);
    try {
      await downloadFile(`/api/drives/${id}/applications/export?${filters}`, {
        expectedType: 'text/csv',
        fallbackName: `drive-${id}-applicants.csv`,
      });
    } catch (err) {
      setExportError(err);
    } finally {
      setExporting(false);
    }
  }

  const filtered = Boolean(status || branch || minCgpa);

  return (
    <div>
      <p>
        <Link to={`/admin/drives/${id}`}>← Back to the drive</Link>
      </p>
      <div className="page-heading">
        <div>
          <h1>Applicants</h1>
          {drive.data && (
            <p className="muted">
              {drive.data.roleTitle} at {drive.data.companyName} · <StatusBadge status={drive.data.status} />
            </p>
          )}
        </div>
        <button
          type="button"
          className="btn btn-primary"
          onClick={handleExport}
          disabled={exporting || !data || data.totalElements === 0}
        >
          {exporting ? 'Exporting…' : filtered ? 'Export filtered CSV' : 'Export CSV'}
        </button>
      </div>
      {/* The drive line above is only a label: if it fails to load, the list below still says why (e.g. 404) */}
      <ErrorAlert error={exportError} />
      <ErrorAlert error={resumeError} />

      <div className="filter-bar">
        <div className="field">
          <label htmlFor="statusFilter">Status</label>
          <select id="statusFilter" value={status} onChange={(e) => changeParams({ status: e.target.value })}>
            <option value="">All statuses</option>
            {APPLICATION_STATUSES.map((s) => (
              <option key={s} value={s}>
                {s.charAt(0) + s.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="branchFilter">Branch</label>
          <select id="branchFilter" value={branch} onChange={(e) => changeParams({ branch: e.target.value })}>
            <option value="">All branches</option>
            {BRANCHES.map((b) => (
              <option key={b.code} value={b.code}>
                {b.code} ({b.name})
              </option>
            ))}
          </select>
        </div>
        <MinCgpaFilter key={minCgpa} value={minCgpa} onApply={(value) => changeParams({ minCgpa: value })} />
        {filtered && (
          <button type="button" className="btn btn-secondary" onClick={() => setSearchParams({})}>
            Clear filters
          </button>
        )}
      </div>

      {/* e.g. 404 "Drive not found", or 400 for a bad ?minCgpa= typed into the URL */}
      {error && <ErrorAlert error={error} onRetry={reload} />}
      {!error && loading && !data && <Loading text="Loading applicants…" />}

      {data && data.content.length === 0 && (
        <EmptyState
          title={
            data.totalElements > 0
              ? 'There are no applicants on this page.'
              : filtered
                ? 'No applicants match these filters.'
                : 'No one has applied to this drive yet.'
          }
        >
          {data.totalElements > 0 && (
            <button type="button" className="btn btn-secondary" onClick={() => changeParams({ page: 0 })}>
              Go to page 1
            </button>
          )}
        </EmptyState>
      )}

      {data && data.content.length > 0 && (
        <>
          <p className="muted">
            {data.totalElements} applicant{data.totalElements === 1 ? '' : 's'}
            {filtered ? ' match these filters' : ''}, sorted by roll number.
          </p>
          <div className="table-wrap card">
            <table className="table">
              <thead>
                <tr>
                  <th>Roll no</th>
                  <th>Student</th>
                  <th>Branch</th>
                  <th>CGPA</th>
                  <th>Backlogs</th>
                  <th>Applied</th>
                  <th>Status</th>
                  <th>Rounds</th>
                  <th>Resume</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((applicant) => (
                  <tr key={applicant.applicationId}>
                    <td className="nowrap">{applicant.rollNo}</td>
                    <td>
                      <strong>{applicant.fullName}</strong>
                      <div className="muted">{applicant.email}</div>
                      {applicant.phone && <div className="muted">{applicant.phone}</div>}
                    </td>
                    <td>{applicant.branch}</td>
                    <td>{Number(applicant.cgpa).toFixed(2)}</td>
                    <td>{applicant.activeBacklogs}</td>
                    <td>{formatDateTime(applicant.appliedAt)}</td>
                    <td>
                      <StatusBadge status={applicant.status} />
                      {applicant.statusReason && <div className="status-reason">{applicant.statusReason}</div>}
                    </td>
                    <td>
                      <Progress progress={applicant.progress} />
                    </td>
                    <td className="nowrap">
                      {applicant.hasResume ? (
                        <button
                          type="button"
                          className="link-button"
                          onClick={() => handleViewResume(applicant.studentId)}
                          disabled={openingResumeOf === applicant.studentId}
                        >
                          {openingResumeOf === applicant.studentId ? 'Opening…' : 'Resume'}
                        </button>
                      ) : (
                        <span className="muted">None</span>
                      )}
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
