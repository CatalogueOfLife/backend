package life.catalogue.api.vocab;

public enum Sex {

    /**
     * Female (♀) is the sex of an organism, or a part of an organism,
     * which produces mobile ova (egg cells).
     */
    FEMALE('♀'),

    /**
     * Male (♂) refers to the sex of an organism, or part of an organism,
     * which produces small mobile gametes, called spermatozoa.
     */
    MALE('♂'),

    /**
     * One organism having both male and female sexual characteristics and organs;
     * at birth an unambiguous assignment of male or female cannot be made
     */
    HERMAPHRODITE('⚥');

    public final char symbol;

    Sex(char symbol) {
        this.symbol = symbol;
    }
}
