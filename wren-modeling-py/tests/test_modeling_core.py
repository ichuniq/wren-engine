import base64
import json

import wren_core

manifest = {
    "catalog": "my_catalog",
    "schema": "my_schema",
    "models": [
        {
            "name": "customer",
            "tableReference": {
                "schema": "main",
                "table": "customer",
            },
            "columns": [
                {"name": "c_custkey", "type": "integer"},
                {"name": "c_name", "type": "varchar"},
            ],
            "primaryKey": "c_custkey",
        },
    ],
}

manifest_str = base64.b64encode(json.dumps(manifest).encode("utf-8")).decode("utf-8")


def test_transform_sql():
    sql = "SELECT * FROM my_catalog.my_schema.customer"
    rewritten_sql = wren_core.transform_sql(manifest_str, sql)
    assert (
        rewritten_sql
        == 'SELECT main.customer.c_custkey AS c_custkey, main.customer.c_name AS c_name FROM (SELECT main.customer.c_custkey, main.customer.c_name FROM main.customer) AS customer'
    )
