-- 修复 PostgreSQL 历史版本中 @Lob String 写入 TEXT/oid 字段时遗留的大对象引用
-- 现象：列里存的是 OID 数字串，JPA 仍能通过 getClob() 读取正文，但原生 SQL / ILIKE 只能看到数字
-- 注意：ALTER COLUMN ... USING 里不能直接写子查询，因此这里先定义辅助函数再做类型转换

CREATE OR REPLACE FUNCTION starlight_read_large_object(value oid)
RETURNS TEXT
LANGUAGE plpgsql
AS $function$
DECLARE
    content BYTEA;
BEGIN
    IF value IS NULL THEN
        RETURN NULL;
    END IF;

    BEGIN
        content := lo_get(value);
        IF content IS NULL THEN
            RETURN value::text;
        END IF;
        RETURN convert_from(content, 'UTF8');
    EXCEPTION
        WHEN OTHERS THEN
            RETURN value::text;
    END;
END;
$function$;

CREATE OR REPLACE FUNCTION starlight_resolve_large_object_reference(value TEXT)
RETURNS TEXT
LANGUAGE plpgsql
AS $function$
DECLARE
    oid_value oid;
BEGIN
    IF value IS NULL OR value !~ '^[0-9]+$' THEN
        RETURN value;
    END IF;

    BEGIN
        oid_value := value::oid;
        RETURN starlight_read_large_object(oid_value);
    EXCEPTION
        WHEN OTHERS THEN
            RETURN value;
    END;
END;
$function$;

DO $repair$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'sl_note'
          AND column_name = 'markdown_content'
          AND udt_name = 'oid'
    ) THEN
        EXECUTE $sql$
            ALTER TABLE sl_note
            ALTER COLUMN markdown_content TYPE TEXT
            USING starlight_read_large_object(markdown_content)
        $sql$;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'sl_note'
          AND column_name = 'rendered_html'
          AND udt_name = 'oid'
    ) THEN
        EXECUTE $sql$
            ALTER TABLE sl_note
            ALTER COLUMN rendered_html TYPE TEXT
            USING starlight_read_large_object(rendered_html)
        $sql$;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'sl_note'
          AND column_name = 'outline_json'
          AND udt_name = 'oid'
    ) THEN
        EXECUTE $sql$
            ALTER TABLE sl_note
            ALTER COLUMN outline_json TYPE TEXT
            USING starlight_read_large_object(outline_json)
        $sql$;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'sl_note'
          AND column_name = 'plain_text'
          AND udt_name = 'oid'
    ) THEN
        EXECUTE $sql$
            ALTER TABLE sl_note
            ALTER COLUMN plain_text TYPE TEXT
            USING starlight_read_large_object(plain_text)
        $sql$;
    END IF;
END $repair$;

UPDATE sl_note
SET markdown_content = starlight_resolve_large_object_reference(markdown_content)
WHERE markdown_content ~ '^[0-9]+$'
  AND starlight_resolve_large_object_reference(markdown_content) IS DISTINCT FROM markdown_content;

UPDATE sl_note
SET rendered_html = starlight_resolve_large_object_reference(rendered_html)
WHERE rendered_html ~ '^[0-9]+$'
  AND starlight_resolve_large_object_reference(rendered_html) IS DISTINCT FROM rendered_html;

UPDATE sl_note
SET outline_json = starlight_resolve_large_object_reference(outline_json)
WHERE outline_json ~ '^[0-9]+$'
  AND starlight_resolve_large_object_reference(outline_json) IS DISTINCT FROM outline_json;

UPDATE sl_note
SET plain_text = starlight_resolve_large_object_reference(plain_text)
WHERE plain_text ~ '^[0-9]+$'
  AND starlight_resolve_large_object_reference(plain_text) IS DISTINCT FROM plain_text;

DROP FUNCTION IF EXISTS starlight_resolve_large_object_reference(TEXT);
DROP FUNCTION IF EXISTS starlight_read_large_object(oid);

