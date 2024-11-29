import base64

# import os
import orjson
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.model.validator import rules

pytestmark = pytest.mark.pyspark

base_url = "/v2/connector/pyspark"

connection_info = {
    "app_name": "MyApp",
    "master": "local",
}

manifest = {
    "catalog": "my_catalog",
    "schema": "my_schema",
    "models": [
        {
            "name": "Orders",
            "properties": {},
            "refSql": "select * from tpch.orders",
            "columns": [
                {"name": "orderkey", "expression": "O_ORDERKEY", "type": "integer"},
                {"name": "custkey", "expression": "O_CUSTKEY", "type": "integer"},
                {
                    "name": "orderstatus",
                    "expression": "O_ORDERSTATUS",
                    "type": "varchar",
                },
                {
                    "name": "totalprice",
                    "expression": "O_TOTALPRICE",
                    "type": "float",
                },
                {"name": "orderdate", "expression": "O_ORDERDATE", "type": "date"},
                {
                    "name": "order_cust_key",
                    "expression": "concat(O_ORDERKEY, '_', O_CUSTKEY)",
                    "type": "varchar",
                },
                {
                    "name": "timestamp",
                    "expression": "cast('2024-01-01T23:59:59' as timestamp)",
                    "type": "timestamp",
                },
                {
                    "name": "timestamptz",
                    "expression": "cast('2024-01-01T23:59:59' as timestamp with time zone)",
                    "type": "timestamp",
                },
                {
                    "name": "test_null_time",
                    "expression": "cast(NULL as timestamp)",
                    "type": "timestamp",
                },
            ],
            "primaryKey": "orderkey",
        },
    ],
}


@pytest.fixture
def manifest_str():
    return base64.b64encode(orjson.dumps(manifest)).decode("utf-8")


with TestClient(app) as client:
    # def test_query(manifest_str):
    #     response = client.post(
    #         url=f"{base_url}/query",
    #         json={
    #             "connectionInfo": connection_info,
    #             "manifestStr": manifest_str,
    #             "sql": 'SELECT * FROM "Orders" ORDER BY "orderkey" LIMIT 1',
    #         },
    #     )
    #     assert response.status_code == 200
    #     result = response.json()
    #     assert len(result["columns"]) == len(manifest["models"][0]["columns"])
    #     assert len(result["data"]) == 1
    #     assert result["data"][0] == [
    #         1,
    #         36901,
    #         "O",
    #         "173665.47",
    #         "1996-01-02",
    #         "1_36901",
    #         "2024-01-01 23:59:59.000000",
    #         "2024-01-01 23:59:59.000000 UTC",
    #         None,
    #     ]
    #     assert result["dtypes"] == {
    #         "orderkey": "int64",
    #         "custkey": "int64",
    #         "orderstatus": "object",
    #         "totalprice": "object",
    #         "orderdate": "object",
    #         "order_cust_key": "object",
    #         "timestamp": "object",
    #         "timestamptz": "object",
    #         "test_null_time": "datetime64[ns]",
    #     }

    def test_query_without_manifest():
        response = client.post(
            url=f"{base_url}/query",
            json={
                "connectionInfo": connection_info,
                "sql": 'SELECT * FROM "Orders" LIMIT 1',
            },
        )
        assert response.status_code == 422
        result = response.json()
        assert result["detail"][0] is not None
        assert result["detail"][0]["type"] == "missing"
        assert result["detail"][0]["loc"] == ["body", "manifestStr"]
        assert result["detail"][0]["msg"] == "Field required"

    def test_query_without_sql(manifest_str):
        response = client.post(
            url=f"{base_url}/query",
            json={"connectionInfo": connection_info, "manifestStr": manifest_str},
        )
        assert response.status_code == 422
        result = response.json()
        assert result["detail"][0] is not None
        assert result["detail"][0]["type"] == "missing"
        assert result["detail"][0]["loc"] == ["body", "sql"]
        assert result["detail"][0]["msg"] == "Field required"

    def test_query_without_connection_info(manifest_str):
        response = client.post(
            url=f"{base_url}/query",
            json={
                "manifestStr": manifest_str,
                "sql": 'SELECT * FROM "Orders" LIMIT 1',
            },
        )
        assert response.status_code == 422
        result = response.json()
        assert result["detail"][0] is not None
        assert result["detail"][0]["type"] == "missing"
        assert result["detail"][0]["loc"] == ["body", "connectionInfo"]
        assert result["detail"][0]["msg"] == "Field required"

    # def test_query_with_dry_run(manifest_str):
    #     response = client.post(
    #         url=f"{base_url}/query",
    #         params={"dryRun": True},
    #         json={
    #             "connectionInfo": connection_info,
    #             "manifestStr": manifest_str,
    #             "sql": 'SELECT * FROM "Orders" LIMIT 1',
    #         },
    #     )
    #     assert response.status_code == 204

    def test_query_with_dry_run_and_invalid_sql(manifest_str):
        response = client.post(
            url=f"{base_url}/query",
            params={"dryRun": True},
            json={
                "connectionInfo": connection_info,
                "manifestStr": manifest_str,
                "sql": "SELECT * FROM X",
            },
        )
        assert response.status_code == 422
        assert response.text is not None

    def test_validate_with_unknown_rule(manifest_str):
        response = client.post(
            url=f"{base_url}/validate/unknown_rule",
            json={
                "connectionInfo": connection_info,
                "manifestStr": manifest_str,
                "parameters": {"modelName": "Orders", "columnName": "orderkey"},
            },
        )
        assert response.status_code == 404
        assert (
            response.text
            == f"The rule `unknown_rule` is not in the rules, rules: {rules}"
        )
