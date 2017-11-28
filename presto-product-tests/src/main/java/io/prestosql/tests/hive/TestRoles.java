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
package io.prestosql.tests.hive;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import io.prestodb.tempto.AfterTestWithContext;
import io.prestodb.tempto.BeforeTestWithContext;
import io.prestodb.tempto.ProductTest;
import io.prestodb.tempto.assertions.QueryAssert;
import io.prestodb.tempto.query.QueryExecutor;
import io.prestodb.tempto.query.QueryResult;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Set;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static io.prestosql.tests.TestGroups.AUTHORIZATION;
import static io.prestosql.tests.TestGroups.PROFILE_SPECIFIC_TESTS;
import static io.prestosql.tests.TestGroups.ROLES;
import static io.prestosql.tests.utils.QueryExecutors.connectToPresto;
import static io.prestosql.tests.utils.QueryExecutors.onHive;
import static io.prestosql.tests.utils.QueryExecutors.onPresto;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

public class TestRoles
        extends ProductTest
{
    private static final String ROLE1 = "role1";
    private static final String ROLE2 = "role2";
    private static final String ROLE3 = "role3";
    private static final Set<String> TEST_ROLES = ImmutableSet.of(ROLE1, ROLE2, ROLE3);

    @BeforeTestWithContext
    public void setUp()
    {
        onHive().executeQuery("SET ROLE admin");
        cleanup();
    }

    @AfterTestWithContext
    public void tearDown()
    {
        cleanup();
    }

    public void cleanup()
    {
        Set<String> existentRoles = listRoles();
        for (String role : TEST_ROLES) {
            if (existentRoles.contains(role)) {
                onHive().executeQuery(format("DROP ROLE %s", role));
            }
        }
    }

    private Set<String> listRoles()
    {
        return onHive().executeQuery("SHOW ROLES").rows().stream()
                .map(Iterables::getOnlyElement)
                .map(String.class::cast)
                .collect(toImmutableSet());
    }

    @Test(groups = {ROLES, AUTHORIZATION, PROFILE_SPECIFIC_TESTS})
    public void testCreateRole()
    {
        onPresto().executeQuery(format("CREATE ROLE %s", ROLE1));
        onPresto().executeQuery(format("CREATE ROLE %s IN hive", ROLE2));
        assertThat(listRoles()).contains(ROLE1, ROLE2);
    }

    @Test(groups = {ROLES, AUTHORIZATION, PROFILE_SPECIFIC_TESTS})
    public void testDropRole()
    {
        onHive().executeQuery(format("CREATE ROLE %s", ROLE1));
        assertThat(listRoles()).contains(ROLE1);
        onPresto().executeQuery(format("DROP ROLE %s", ROLE1));
        assertThat(listRoles()).doesNotContain(ROLE1);
    }

    @Test(groups = {ROLES, AUTHORIZATION, PROFILE_SPECIFIC_TESTS})
    public void testListRoles()
    {
        onPresto().executeQuery(format("CREATE ROLE %s", ROLE1));
        onPresto().executeQuery(format("CREATE ROLE %s IN hive", ROLE2));
        QueryResult expected = onHive().executeQuery("SHOW ROLES");
        QueryResult actual = onPresto().executeQuery("SELECT * FROM hive.information_schema.roles");
        assertThat(actual.rows()).containsExactly(expected.rows().toArray(new List[] {}));
    }

    @Test(groups = {ROLES, AUTHORIZATION, PROFILE_SPECIFIC_TESTS})
    public void testCreateDuplicateRole()
    {
        onPresto().executeQuery(format("CREATE ROLE %s", ROLE1));
        QueryAssert.assertThat(() -> onPresto().executeQuery(format("CREATE ROLE %s", ROLE1)))
                .failsWithMessage(format("Role '%s' already exists", ROLE1));
    }

    @Test(groups = {ROLES, AUTHORIZATION, PROFILE_SPECIFIC_TESTS})
    public void testDropNonExistentRole()
    {
        QueryAssert.assertThat(() -> onPresto().executeQuery(format("DROP ROLE %s", ROLE3)))
                .failsWithMessage(format("Role '%s' does not exist", ROLE3));
    }

    @Test(groups = {ROLES, AUTHORIZATION, PROFILE_SPECIFIC_TESTS})
    public void testAccessControl()
    {
        // Only users that are granted with "admin" role can create, drop and list roles
        // Alice is not granted with "admin" role
        QueryAssert.assertThat(() -> onPrestoAlice().executeQuery(format("CREATE ROLE %s", ROLE3)))
                .failsWithMessage(format("Cannot create role %s", ROLE3));
        QueryAssert.assertThat(() -> onPrestoAlice().executeQuery(format("DROP ROLE %s", ROLE3)))
                .failsWithMessage(format("Cannot drop role %s", ROLE3));
        QueryAssert.assertThat(() -> onPrestoAlice().executeQuery("SELECT * FROM hive.information_schema.roles"))
                .failsWithMessage("Cannot select from table information_schema.roles");
    }

    private static QueryExecutor onPrestoAlice()
    {
        return connectToPresto("alice@presto");
    }
}
