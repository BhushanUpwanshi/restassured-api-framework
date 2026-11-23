package io.github.bhushanupwanshi.api.tests.booking;

import static io.github.bhushanupwanshi.api.core.assertions.ApiAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.bhushanupwanshi.api.core.models.response.BookingId;
import io.github.bhushanupwanshi.api.tests.BaseApiTest;
import io.github.bhushanupwanshi.api.tests.data.Datasets;
import io.github.bhushanupwanshi.api.tests.data.SearchCase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/** Search filters, driven by {@code booking-search.json}. */
public class SearchTests extends BaseApiTest {

    @DataProvider(name = "search")
    public Object[][] searchCases() {
        return Datasets.rows("booking-search", SearchCase.class);
    }

    @Test(dataProvider = "search", groups = "regression")
    public void aQueryReturnsExactlyTheBookingsItShould(SearchCase testCase) {
        nameCase(testCase);
        String uid = UUID.randomUUID().toString().substring(0, 8);

        Integer seededId = null;
        if (testCase.seed() != null) {
            var created = bookings().createRaw(substitute(testCase.seed(), uid));
            assertThat(created).as("%s: seed booking", testCase.id()).hasStatus(200);
            seededId = created.requireBody().bookingid();
            cleanUpLater(seededId);
        }

        var result = bookings().search(substitute(testCase.query(), uid));
        assertThat(result).hasStatus(200);
        List<Integer> ids = result.requireBody().stream().map(BookingId::bookingid).toList();

        if (Boolean.TRUE.equals(testCase.expectSeed())) {
            assertThat(ids).as("%s: results contain the seeded booking", testCase.id()).contains(seededId);
        }
        if (Boolean.FALSE.equals(testCase.expectSeed()) && seededId != null) {
            assertThat(ids).as("%s: results exclude the seeded booking", testCase.id()).doesNotContain(seededId);
        }
        if (Boolean.TRUE.equals(testCase.expectEmpty())) {
            assertThat(ids).as("%s: no results", testCase.id()).isEmpty();
        }
    }

    /** Replaces {@code ${uid}} in every string value, recursing into nested maps. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> substitute(Map<String, Object> source, String uid) {
        Map<String, Object> out = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (value instanceof String text) {
                out.put(key, text.replace("${uid}", uid));
            } else if (value instanceof Map<?, ?> nested) {
                out.put(key, substitute((Map<String, Object>) nested, uid));
            } else {
                out.put(key, value);
            }
        });
        return out;
    }
}
