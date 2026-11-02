import { Outlet } from 'react-router';
import Header from './Header.jsx';

/** The frame around every logged-in page. <Outlet /> is where the matching child route's page appears. */
export default function AppLayout() {
  return (
    <>
      <Header />
      <main className="page">
        <Outlet />
      </main>
    </>
  );
}
