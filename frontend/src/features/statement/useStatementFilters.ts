import { useCallback, useSyncExternalStore } from 'react';

const POPSTATE = 'popstate';

function subscribe(listener: () => void) {
  window.addEventListener(POPSTATE, listener);
  return () => window.removeEventListener(POPSTATE, listener);
}

const readSearch = () => window.location.search;

/** A URL é a fonte da verdade: recarregar ou compartilhar o link mantém os filtros e a página. */
export function useStatementFilters() {
  const search = useSyncExternalStore(subscribe, readSearch);
  const params = new URLSearchParams(search);

  /** Mudar um filtro volta à primeira página; só a paginação informa a própria `page`. */
  const update = useCallback((changes: Record<string, string>) => {
    const next = new URLSearchParams(window.location.search);
    next.delete('page');
    Object.entries(changes).forEach(([name, value]) => {
      if (value) next.set(name, value);
      else next.delete(name);
    });
    window.history.pushState(null, '', `${window.location.pathname}?${next}`);
    window.dispatchEvent(new PopStateEvent(POPSTATE));
  }, []);

  return { params, update };
}
