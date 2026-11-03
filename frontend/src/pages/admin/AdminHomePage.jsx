import { Link } from 'react-router';
import { useApi } from '../../api/useApi.js';
import EmptyState from '../../components/EmptyState.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import Loading from '../../components/Loading.jsx';
import StatusBadge from '../../components/StatusBadge.jsx';
import { branchLabel } from '../../utils/branches.js';

/** "12.50", or "—" while nobody is placed (the backend sends null). */
function lpa(value) {
  return value == null ? '—' : Number(value).toFixed(2);
}

function StatTile({ value, label }) {
  return (
    <div className="stat-tile">
      <span className="stat-number">{value}</span>
      <span className="muted">{label}</span>
    </div>
  );
}

/** Admin home: the numbers from /api/stats as tiles and tables (no charts), and links to the admin pages. */
export default function AdminHomePage() {
  const { data: stats, loading, error, reload } = useApi('/api/stats');

  return (
    <div className="stack">
      <section className="card">
        <h1>TPO admin home</h1>
        <p>
          Manage <Link to="/admin/companies">companies</Link> and <Link to="/admin/drives">drives</Link> (rounds,
          results and selection are on each drive's page), <Link to="/admin/policies">placement policies</Link>, or read
          the <Link to="/admin/audit">audit log</Link>.
        </p>
        {error && <ErrorAlert error={error} onRetry={reload} />}
        {!error && loading && !stats && <Loading text="Loading stats…" />}
      </section>

      {stats && (
        <>
          <section className="card">
            <h2>Placements</h2>
            <div className="stat-grid">
              <StatTile value={stats.totalStudents} label="Students" />
              <StatTile value={stats.placedStudents} label="Placed" />
              <StatTile value={`${Number(stats.placementPercentage).toFixed(2)}%`} label="Placement rate" />
            </div>
            <h2>CTC of placed students (LPA)</h2>
            <div className="stat-grid">
              <StatTile value={lpa(stats.ctc.highestLpa)} label="Highest" />
              <StatTile value={lpa(stats.ctc.averageLpa)} label="Average" />
              <StatTile value={lpa(stats.ctc.medianLpa)} label="Median" />
            </div>
            <p className="hint">Each placed student's best full-time offer counts once.</p>
          </section>

          <section className="card">
            <h2>Drives by status</h2>
            <div className="stat-grid">
              {Object.entries(stats.drivesByStatus).map(([status, count]) => (
                <div key={status} className="stat-tile">
                  <span className="stat-number">{count}</span>
                  <StatusBadge status={status} />
                </div>
              ))}
            </div>
          </section>

          <section className="card">
            <h2>By branch</h2>
            {stats.branches.length === 0 ? (
              <EmptyState title="No students yet." />
            ) : (
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Branch</th>
                      <th>Students</th>
                      <th>Placed</th>
                      <th>Placement rate</th>
                    </tr>
                  </thead>
                  <tbody>
                    {stats.branches.map((row) => (
                      <tr key={row.branch}>
                        <td>{branchLabel(row.branch)}</td>
                        <td>{row.totalStudents}</td>
                        <td>{row.placedStudents}</td>
                        <td>{Number(row.placementPercentage).toFixed(2)}%</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <section className="card">
            <h2>Offers by company</h2>
            {stats.offersByCompany.length === 0 ? (
              <EmptyState title="No offers yet." />
            ) : (
              <div className="table-wrap">
                <table className="table">
                  <thead>
                    <tr>
                      <th>Company</th>
                      <th>Offers</th>
                    </tr>
                  </thead>
                  <tbody>
                    {stats.offersByCompany.map((row) => (
                      <tr key={row.companyId}>
                        <td>{row.companyName}</td>
                        <td>{row.offers}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}
    </div>
  );
}
