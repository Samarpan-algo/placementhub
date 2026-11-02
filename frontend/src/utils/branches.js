// Must match the backend's Branch enum (common/Branch.java)
export const BRANCHES = [
  { code: 'CSE', name: 'Computer Science' },
  { code: 'IT', name: 'Information Technology' },
  { code: 'ECE', name: 'Electronics & Communication' },
  { code: 'EEE', name: 'Electrical & Electronics' },
  { code: 'ME', name: 'Mechanical' },
  { code: 'CE', name: 'Civil' },
];

/** "CSE (Computer Science)" for a branch code; the code itself if it's unknown. */
export function branchLabel(code) {
  const branch = BRANCHES.find((b) => b.code === code);
  return branch ? `${branch.code} (${branch.name})` : code;
}
