/**
 * "Eligible" or "Not eligible" plus EVERY reason the backend gave (criteria first, then placement policies).
 * Used on the drives list and the drive detail page, so both explain a "no" the same way.
 */
export default function Eligibility({ eligible, reasons }) {
  if (eligible) {
    return <p className="eligibility eligibility-yes">✓ You are eligible</p>;
  }
  return (
    <div className="eligibility eligibility-no">
      <p>✗ Not eligible</p>
      {reasons.length > 0 && (
        <ul>
          {reasons.map((reason) => (
            <li key={reason}>{reason}</li>
          ))}
        </ul>
      )}
    </div>
  );
}
