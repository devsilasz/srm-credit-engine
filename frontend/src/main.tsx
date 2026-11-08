import '@fontsource-variable/libre-franklin';
import '@fontsource-variable/source-serif-4';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { OperatorPanel } from './features/operator/OperatorPanel';
import { StatementGrid } from './features/statement/StatementGrid';
import './styles.css';

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <header className="masthead">
        <h1>Mesa de liquidação</h1>
        <p>SRM Credit Engine</p>
      </header>
      <main>
        <OperatorPanel />
        <StatementGrid />
      </main>
    </QueryClientProvider>
  </StrictMode>,
);
