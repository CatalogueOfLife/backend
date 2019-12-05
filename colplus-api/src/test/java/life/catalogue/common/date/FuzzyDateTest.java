package life.catalogue.common.date;

import life.catalogue.api.jackson.SerdeTestBase;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class FuzzyDateTest extends SerdeTestBase<FuzzyDate> {
    Random rnd = new Random();

    public FuzzyDateTest() {
        super(FuzzyDate.class);
    }

    @Override
    public FuzzyDate genTestValue() throws Exception {
        FuzzyDate fd = FuzzyDate.of(1800 + rnd.nextInt(230), 1 + rnd.nextInt(12));
        return fd;
    }

    @Test
    public void testOfString() {
        assertEquals(FuzzyDate.of(1919), FuzzyDate.of("1919"));
        assertEquals(FuzzyDate.of(1919,8), FuzzyDate.of("1919-08"));
        assertEquals(FuzzyDate.of(1919,8, 7), FuzzyDate.of("1919-08-07"));
        assertEquals(FuzzyDate.of(1919,12, 31), FuzzyDate.of("1919-12-31"));
    }

    @Test
    public void testToString() {
        FuzzyDate fd = FuzzyDate.of(1919);
        assertEquals("1919", fd.toString());

        fd = FuzzyDate.of(1919, 8);
        assertEquals("1919-08", fd.toString());

        fd = FuzzyDate.of(1919, 8, 17);
        assertEquals("1919-08-17", fd.toString());
    }

}