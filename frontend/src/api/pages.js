import api from './client.js';
import { isApiJson } from './errors.js';

// The backend caps ?size= at 100 (spring.data.web.pageable.max-page-size)
const MAX_PAGE_SIZE = 100;

/**
 * GET every page of a paged list and return it as one response, for screens that must show the whole
 * list at once (a results sheet can't be split across pages). A fixed sort on the backend keeps the order.
 */
export async function getAllPages(url) {
  const first = await api.get(`${url}?page=0&size=${MAX_PAGE_SIZE}`);
  if (!isApiJson(first.data)) return first; // useApi reports it as an unexpected answer
  const content = [...first.data.content];
  for (let page = 1; page < first.data.totalPages; page += 1) {
    const next = await api.get(`${url}?page=${page}&size=${MAX_PAGE_SIZE}`);
    content.push(...next.data.content);
  }
  return { ...first, data: { ...first.data, content } };
}
