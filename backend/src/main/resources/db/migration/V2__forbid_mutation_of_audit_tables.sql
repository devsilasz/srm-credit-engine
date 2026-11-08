-- "Alterar uma liquidação não é uma operação do sistema": a regra vale para qualquer cliente do
-- banco, não só para a aplicação. O histórico de câmbio segue a mesma regra porque a auditoria
-- de uma liquidação depende da linha de taxa que ela referencia.
CREATE FUNCTION forbid_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION '% em % é proibido: tabela imutável', TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER settlement_forbid_update_delete
    BEFORE UPDATE OR DELETE ON settlement
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TRIGGER settlement_forbid_truncate
    BEFORE TRUNCATE ON settlement
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();

CREATE TRIGGER fx_rate_forbid_update_delete
    BEFORE UPDATE OR DELETE ON fx_rate
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TRIGGER fx_rate_forbid_truncate
    BEFORE TRUNCATE ON fx_rate
    FOR EACH STATEMENT EXECUTE FUNCTION forbid_mutation();
