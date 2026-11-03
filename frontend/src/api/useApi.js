import { useCallback, useEffect, useState } from 'react';
import api from './client.js';
import { getAllPages } from './pages.js';
import { isApiJson, toApiError, unexpectedResponseError } from './errors.js';

/**
 * A custom hook (a function whose name starts with "use" and that calls other hooks) for
 * "load data when the page opens". It runs GET url and returns { data, loading, error, reload }.
 *
 * - It loads again whenever `url` changes (e.g. ?page=2), and when you call reload() (e.g. after Apply).
 * - Exactly one of data / error is set once loading ends. A page shows its fields only when data is set,
 *   so a failed load shows an error, never a page full of empty values.
 * - allPages: true fetches every page of a paged list (see pages.js); pass the URL without page or size.
 * - A 200 answer that isn't our JSON (e.g. the dev server's index.html when the /api proxy isn't
 *   active) also counts as an error: see decisions.md #68.
 */
export function useApi(url, { allPages = false } = {}) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);
  // Bumping this number re-runs the effect below, which is all reload() needs to do
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let ignore = false; // set by the cleanup if the url changes or the page goes away mid-request
    setLoading(true);
    (allPages ? getAllPages(url) : api.get(url))
      .then((response) => {
        if (ignore) return;
        if (isApiJson(response.data)) {
          setData(response.data);
          setError(null);
        } else {
          setData(null);
          setError(unexpectedResponseError(response.status));
        }
      })
      .catch((err) => {
        if (ignore) return;
        setData(null);
        setError(toApiError(err));
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [url, allPages, reloadKey]);

  // useCallback keeps the same function between renders, so it is safe to pass to buttons and effects
  const reload = useCallback(() => setReloadKey((key) => key + 1), []);

  return { data, loading, error, reload };
}
