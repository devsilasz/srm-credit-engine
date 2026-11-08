CREATE TABLE assignor (
    id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name     VARCHAR(120) NOT NULL,
    document VARCHAR(14)  NOT NULL UNIQUE
);

CREATE TABLE receivable (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assignor_id BIGINT         NOT NULL REFERENCES assignor (id),
    type        VARCHAR(30)    NOT NULL CHECK (type IN ('DUPLICATA_MERCANTIL', 'CHEQUE_PRE_DATADO')),
    face_value  NUMERIC(19, 2) NOT NULL CHECK (face_value > 0),
    due_date    DATE           NOT NULL,
    status      VARCHAR(10)    NOT NULL CHECK (status IN ('OPEN', 'SETTLED')),
    version     BIGINT         NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL
);

-- rate = BRL por 1 unidade da moeda cotada (USD-BRL 5.4321 significa R$ 5,4321 por US$ 1).
CREATE TABLE fx_rate (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pair       VARCHAR(7)     NOT NULL CHECK (pair IN ('USD-BRL')),
    rate       NUMERIC(19, 8) NOT NULL CHECK (rate > 0),
    valid_from TIMESTAMPTZ    NOT NULL,
    source     VARCHAR(10)    NOT NULL CHECK (source IN ('MANUAL', 'PROVIDER')),
    created_at TIMESTAMPTZ    NOT NULL
);

-- Atende "a taxa vigente": a mais recente de um par com valid_from <= agora.
CREATE INDEX idx_fx_rate_pair_valid_from ON fx_rate (pair, valid_from DESC);

-- Registro de auditoria: guarda uma cópia do que foi usado no cálculo, não referências que mudam.
CREATE TABLE settlement (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    receivable_id         BIGINT         NOT NULL REFERENCES receivable (id),
    assignor_id           BIGINT         NOT NULL REFERENCES assignor (id),
    receivable_type       VARCHAR(30)    NOT NULL CHECK (receivable_type IN ('DUPLICATA_MERCANTIL', 'CHEQUE_PRE_DATADO')),
    face_value            NUMERIC(19, 2) NOT NULL CHECK (face_value > 0),
    term_months           INTEGER        NOT NULL CHECK (term_months > 0),
    base_rate_monthly     NUMERIC(9, 6)  NOT NULL,
    spread_monthly        NUMERIC(9, 6)  NOT NULL,
    present_value_brl     NUMERIC(19, 2) NOT NULL CHECK (present_value_brl > 0),
    discount              NUMERIC(19, 2) NOT NULL,
    payment_currency      VARCHAR(3)     NOT NULL CHECK (payment_currency IN ('BRL', 'USD')),
    present_value_payment NUMERIC(19, 2) NOT NULL CHECK (present_value_payment > 0),
    fx_rate_id            BIGINT REFERENCES fx_rate (id),
    fx_rate               NUMERIC(19, 8),
    idempotency_key       UUID           NOT NULL,
    request_hash          VARCHAR(64)    NOT NULL,
    settled_at            TIMESTAMPTZ    NOT NULL,
    -- Nomes fixos: o serviço distingue qual UNIQUE foi violada para decidir entre replay e 409.
    CONSTRAINT uq_settlement_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT uq_settlement_receivable UNIQUE (receivable_id),
    CONSTRAINT ck_settlement_values_add_up CHECK (present_value_brl + discount = face_value),
    CONSTRAINT ck_settlement_fx_iff_foreign CHECK (
        (payment_currency = 'BRL' AND fx_rate_id IS NULL AND fx_rate IS NULL)
        OR (payment_currency <> 'BRL' AND fx_rate_id IS NOT NULL AND fx_rate IS NOT NULL)
    )
);

-- Extrato por período, sem cedente. O Postgres lê o índice de trás para frente no ORDER BY desc.
CREATE INDEX idx_settlement_settled_at ON settlement (settled_at);
-- Extrato por cedente e período. Sem índice para moeda: só 2 valores, pouco seletivo.
CREATE INDEX idx_settlement_assignor_settled_at ON settlement (assignor_id, settled_at);
