/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.cml.pgcatalog.table;

import io.cml.spi.metadata.TableMetadata;

import static io.cml.pgcatalog.table.PgCatalogTableUtils.table;
import static io.cml.type.IntegerType.INTEGER;
import static io.cml.type.PGArray.VARCHAR_ARRAY;
import static io.cml.type.VarcharType.VARCHAR;

/**
 * @see <a href="https://www.postgresql.org/docs/13/catalog-pg-namespace.html">PostgreSQL pg_namespace</a>
 */
public class PgNamespaceTable
        extends PgCatalogTable
{
    public static final String NAME = "pg_namespace";

    @Override
    protected TableMetadata createMetadata()
    {
        return table(NAME)
                .column("oid", INTEGER, "${hash}(${schemaName})")
                .column("nspname", VARCHAR, "${schemaName}")
                .column("nspowner", INTEGER, "0")
                .column("nspacl", VARCHAR_ARRAY, "null")
                .build();
    }
}
