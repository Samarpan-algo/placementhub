import StatusBadge from './StatusBadge.jsx';

/** Applicants a sheet leaves out, each with why: [{ applicant, why }]. Renders nothing for an empty list. */
export default function ApplicantReasonTable({ title, rows }) {
  if (rows.length === 0) {
    return null;
  }
  return (
    <section>
      <h2>
        {title} ({rows.length})
      </h2>
      <div className="table-wrap card">
        <table className="table">
          <thead>
            <tr>
              <th>Roll no</th>
              <th>Student</th>
              <th>Status</th>
              <th>Why</th>
            </tr>
          </thead>
          <tbody>
            {rows.map(({ applicant, why }) => (
              <tr key={applicant.applicationId}>
                <td className="nowrap">{applicant.rollNo}</td>
                <td>{applicant.fullName}</td>
                <td>
                  <StatusBadge status={applicant.status} />
                </td>
                <td className="cell-text">{why}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
