package org.tdar.nlp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class NumericCountTest {

    @Test
    public void test() {
        String key = "42 1/22/27 V";
        int per = Utils.percentNumericCharacters(key);
        System.out.println(per);
        assertEquals(87, per);
        
        key = "122 12/21/27 HI NE";
        per = Utils.percentNumericCharacters(key);
        System.out.println(per);
    }
    
    
}
