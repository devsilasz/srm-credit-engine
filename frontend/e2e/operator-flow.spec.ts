import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';

const API_URL = process.env.API_URL ?? 'http://localhost:8080';
const SCREENSHOT_DIR = process.env.SCREENSHOT_DIR ?? '/results';
const ASSIGNOR = 'Metalúrgica Aurora Ltda';
const TIME_ZONE = 'America/Sao_Paulo';

function todayInBusinessZone(): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: TIME_ZONE }).format(new Date());
}

/** Soma meses como o backend (`LocalDate.plusMonths`): dia 31 + 1 mês cai no fim do mês seguinte. */
function plusMonths(isoDate: string, months: number): string {
  const [year, month, day] = isoDate.split('-').map(Number);
  const target = new Date(Date.UTC(year, month - 1 + months, 1));
  const lastDay = new Date(
    Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0),
  ).getUTCDate();
  target.setUTCDate(Math.min(day, lastDay));
  return target.toISOString().slice(0, 10);
}

/** Massa para a paginação, direto pela API: o que interessa aqui é o grid. */
async function seedSettlements(request: APIRequestContext, assignorId: number, count: number) {
  for (let i = 0; i < count; i++) {
    const created = await request.post(`${API_URL}/api/receivables`, {
      data: {
        assignorId,
        type: 'CHEQUE_PRE_DATADO',
        faceValue: '25000.00',
        dueDate: plusMonths(todayInBusinessZone(), 2),
      },
    });
    expect(created.status()).toBe(201);
    const { id } = await created.json();
    const settled = await request.post(`${API_URL}/api/settlements`, {
      headers: { 'Idempotency-Key': randomUUID() },
      data: { receivableId: id, paymentCurrency: 'BRL' },
    });
    expect(settled.status()).toBe(201);
  }
}

function countRequests(page: Page, method: string, urlPart: string) {
  const seen: { key: string | undefined }[] = [];
  page.on('request', (request) => {
    if (request.method() === method && new URL(request.url()).pathname === urlPart) {
      seen.push({ key: request.headers()['idempotency-key'] });
    }
  });
  return seen;
}

test('fluxo do operador: simular, liquidar, repetir com segurança e conferir o extrato', async ({
  page,
  request,
}) => {
  const assignors = await (await request.get(`${API_URL}/api/assignors`)).json();
  const assignorId: number = assignors.find((a: { name: string }) => a.name === ASSIGNOR).id;
  await seedSettlements(request, assignorId, 21);

  const quotes = countRequests(page, 'POST', '/api/pricing/quote');
  const receivables = countRequests(page, 'POST', '/api/receivables');
  const settlements = countRequests(page, 'POST', '/api/settlements');

  await page.goto('/');
  const settleButton = page.getByRole('button', { name: 'Liquidar' });
  const faceValue = page.getByLabel('Valor de face, em reais');
  const dueDate = page.getByLabel('Vencimento', { exact: true });
  const threeMonths = plusMonths(todayInBusinessZone(), 3);

  await test.step('AC-UI-04 campo inválido: mensagem no campo, sem API e sem botão', async () => {
    await faceValue.fill('0');
    await expect(page.getByText('O valor precisa ser maior que zero.')).toBeVisible();
    await dueDate.fill(todayInBusinessZone());
    await expect(page.getByText('O vencimento precisa ser depois de hoje.')).toBeVisible();
    await page.waitForTimeout(500);
    expect(quotes).toHaveLength(0);
    await expect(settleButton).toBeDisabled();
  });

  await test.step('AC-UI-01 e AC-UI-03 simulação em até 1 s, no formato pt-BR', async () => {
    await page.getByLabel('Cedente', { exact: true }).first().selectOption({ label: ASSIGNOR });
    await dueDate.fill(threeMonths);
    await faceValue.fill('100000,00');
    await expect(page.getByTestId('present-value')).toHaveText('R$ 92.859,94', { timeout: 1000 });
    await expect(page.getByTestId('discount')).toHaveText('− R$ 7.140,06');
    await expect(settleButton).toBeEnabled();
  });

  await test.step('AC-UI-02 em dólar, mostra prazo, taxas e câmbio com vigência', async () => {
    await page.getByLabel('Dólar').check();
    await expect(page.getByTestId('present-value')).toHaveText('US$ 17.094,67');
    const sheet = page.getByTestId('quote');
    await expect(sheet).toContainText('3 meses a 2,50% a.m. (base 1,00% + spread 1,50%)');
    await expect(sheet).toContainText('5,4321');
    await expect(sheet).toContainText('vigente desde');
    await page.screenshot({ path: `${SCREENSHOT_DIR}/simulacao-em-dolar.png` });
    await page.getByLabel('Real').check();
    await expect(page.getByTestId('present-value')).toHaveText('R$ 92.859,94');
  });

  await test.step('AC-UI-05 recusa da API aparece com o texto legível, sem JSON cru', async () => {
    await dueDate.fill(plusMonths(todayInBusinessZone(), 361));
    await expect(page.getByRole('alert')).toHaveText('O prazo máximo é de 360 meses.');
    await expect(settleButton).toBeDisabled();
    await dueDate.fill(threeMonths);
    await expect(page.getByTestId('present-value')).toHaveText('R$ 92.859,94');
  });

  const firstSettlementId = await test.step('AC-UI-11 resposta perdida: repete sem pagar de novo', async () => {
    // O servidor liquida, mas a resposta não chega: é o cenário de pagamento em dobro do Anexo B.
    await page.route('**/api/settlements', async (route) => {
      await route.fetch();
      await route.abort('connectionreset');
    });
    await settleButton.click();
    await expect(page.getByRole('alert')).toContainText('Sem resposta do servidor');
    await page.unrouteAll();

    await settleButton.click();
    await expect(page.getByTestId('settled')).toContainText('Liquidada nº');

    expect(receivables).toHaveLength(1);
    expect(settlements).toHaveLength(2);
    expect(settlements[0].key).toBeTruthy();
    expect(settlements[1].key).toBe(settlements[0].key);
    return ((await page.getByTestId('settled').textContent()) ?? '').match(/nº (\d+)/)![1];
  });

  await test.step('AC-UI-07 confirmação com o id e grid atualizado', async () => {
    await expect(page.getByTestId('statement').locator('tbody tr').first()).toContainText(
      firstSettlementId,
    );
    await expect(settleButton).toBeDisabled();
    await page.screenshot({ path: `${SCREENSHOT_DIR}/painel-do-operador.png`, fullPage: true });
  });

  await test.step('AC-UI-06 duplo clique envia uma única liquidação', async () => {
    await page.getByRole('button', { name: 'Nova operação' }).click();
    receivables.length = 0;
    settlements.length = 0;
    await page.getByLabel('Cedente', { exact: true }).first().selectOption({ label: ASSIGNOR });
    await faceValue.fill('50000,00');
    await dueDate.fill(threeMonths);
    await expect(settleButton).toBeEnabled();
    // Os dois cliques no mesmo tick: sem re-render no meio, o `disabled` não pode ajudar.
    await settleButton.evaluate((button: HTMLButtonElement) => {
      button.click();
      button.click();
    });
    await expect(page.getByTestId('settled')).toContainText('Liquidada nº');
    expect(receivables).toHaveLength(1);
    expect(settlements).toHaveLength(1);
  });

  await test.step('AC-UI-08 paginação e filtros vão ao servidor e ficam na URL', async () => {
    await page.getByLabel('Moeda', { exact: true }).selectOption('BRL');
    await page.getByRole('combobox', { name: 'Cedente' }).nth(1).selectOption({ label: ASSIGNOR });
    await expect(page).toHaveURL(/currency=BRL/);
    await expect(page).toHaveURL(new RegExp(`assignorId=${assignorId}`));

    const secondPage = page.waitForRequest((r) => /[?&]page=1(&|$)/.test(r.url()));
    await page.getByRole('button', { name: 'Próxima página' }).click();
    const pageRequest = new URL((await secondPage).url());
    expect(pageRequest.pathname).toBe('/api/settlements');
    expect(pageRequest.searchParams.get('size')).toBe('20');
    expect(pageRequest.searchParams.get('currency')).toBe('BRL');
    expect(pageRequest.searchParams.get('assignorId')).toBe(String(assignorId));
    await expect(page.getByTestId('page-status')).toContainText('Página 2 de');
    await expect(page).toHaveURL(/page=1/);

    await page.reload();
    await expect(page.getByTestId('page-status')).toContainText('Página 2 de');
    await expect(page.getByLabel('Moeda', { exact: true })).toHaveValue('BRL');
  });

  await test.step('Liquidação em dólar: valor e câmbio chegam ao carimbo e ao grid', async () => {
    await page.goto('/');
    await page.getByLabel('Cedente', { exact: true }).first().selectOption({ label: ASSIGNOR });
    await faceValue.fill('100000,00');
    await dueDate.fill(threeMonths);
    await page.getByLabel('Dólar').check();
    await expect(page.getByTestId('present-value')).toHaveText('US$ 17.094,67');
    await settleButton.click();
    await expect(page.getByTestId('settled')).toContainText('US$ 17.094,67');

    await page.getByLabel('Moeda', { exact: true }).selectOption('USD');
    await expect(page).toHaveURL(/currency=USD/);
    const firstRow = page.getByTestId('statement').locator('tbody tr').first();
    await expect(firstRow).toContainText('US$ 17.094,67');
    await expect(firstRow).toContainText('5,4321');
  });

  await test.step('Filtro de período: hoje traz a liquidação, e um período antigo fica vazio', async () => {
    const today = todayInBusinessZone();
    await page.getByLabel('De', { exact: true }).fill(today);
    await page.getByLabel('Até', { exact: true }).fill(today);
    await expect(page).toHaveURL(new RegExp(`from=${today}`));
    await expect(page.getByTestId('statement').locator('tbody tr').first()).toBeVisible();

    await page.getByLabel('De', { exact: true }).fill('2020-01-01');
    await page.getByLabel('Até', { exact: true }).fill('2020-01-31');
    await expect(page.getByText('Nenhuma liquidação com estes filtros.')).toBeVisible();
  });

  await test.step('A documentação prometida no README está publicada', async () => {
    await page.goto(`${API_URL}/swagger-ui.html`);
    await expect(page.getByText('SRM Credit Engine API').first()).toBeVisible();
  });
});
