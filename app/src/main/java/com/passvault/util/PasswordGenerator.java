package com.passvault.util;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Cryptographically secure generator.
 *
 * Uses SecureRandom, not Random. It guarantees at least one character from every
 * selected class, then shuffles — so the required characters are not stuck in
 * predictable positions, which is a common bug in naive generators.
 */
public final class PasswordGenerator {

    public static final String LOWER = "abcdefghijkmnopqrstuvwxyz";   // no l
    public static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";    // no I, O
    public static final String DIGITS = "23456789";                   // no 0, 1
    public static final String SYMBOLS = "!@#$%^&*()-_=+[]{};:,.<>?";

    public static final String LOWER_FULL = "abcdefghijklmnopqrstuvwxyz";
    public static final String UPPER_FULL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    public static final String DIGITS_FULL = "0123456789";

    /**
     * Word list for passphrase mode. 1492 words, so each word is worth
     * log2(1492) = 10.5 bits. Five words gives roughly 53 bits, which is
     * stronger than almost any password a person will type from memory.
     *
     * A production build should ship the EFF 7776-word long list as an asset;
     * this is inlined to keep the app dependency-free.
     */
    private static final String[] WORDS = (
                      "able acid acorn actor adapt admit adobe adopt agent agile alarm album alert alien "
                    + "alike alive alley alloy alpha amber amble amend anchor angle ankle apple apron arbor "
                    + "arena argue armor arrow ashes aside asset atlas attic audio audit avoid awake award "
                    + "aware badge bagel baker balmy banjo barge basil basin basket batch beach beacon beam "
                    + "bean bear beast bench berry bicep bike bill birch bison black blade blaze blend blimp "
                    + "blink bliss block bloom blues bluff blunt board boast bolt bonus booth boots boulder "
                    + "bounce bowl brace braid brain brake branch brass brave bread break breeze brick bridge "
                    + "brief bright bring brisk broad broil bronze brook broom brown brush bubble bucket "
                    + "buddy budge buffalo bugle build bulk bunch bundle bunny burst bushel butter cabin "
                    + "cable cactus cadet camel cameo canal candle canoe canvas canyon carat cargo carol "
                    + "carpet carrot carve casket castle cattle cause cedar cello census chain chair chalk "
                    + "champ chant chaos charm chase cheek cheer chess chest chief chill chime chip chirp "
                    + "chisel chorus chrome chunk cider cinder circle citrus civic clamp clash clasp classic "
                    + "clay clean clear cleat clerk cliff climb cling clock close cloth cloud clove clown "
                    + "clump coach coast cobalt cobra cocoa coffee coil coin comet comic compass cone coral "
                    + "cord cork corn cosmic cotton couch cough court cover cozy crab craft crane crate crawl "
                    + "cream creek crest crew crisp crop cross crowd crown crumb crust crypt cube cuff curb "
                    + "curl curve cycle daisy dance dandy dapper dart dash dawn debate debut decal decay "
                    + "decoy deed deep deer delta demo dense depot depth derby desert desk detail dial diary "
                    + "dice diesel digit dime diner dinner ditch dive dizzy dock dodge dogma dolphin domain "
                    + "dome donor donut doodle dove down draft drama drape dream dress drift drill drink "
                    + "drive drone drove drum dryer duck duel duet dune dusk dusty eagle early earn earth "
                    + "easel east easy echo eclipse edge edit eight elbow elder elect elite ember emboss "
                    + "empty enact enamel endure engine enjoy enter envoy epoch equal equip erase escape "
                    + "essay etch ether event evoke exact exert exile exit expand extra fable fabric face "
                    + "facet fade fairy falcon fancy fang fare farm fast fault feast feather fence fern ferry "
                    + "fetch fever fiber fiddle field fierce fifty figure filter final finch finish fire "
                    + "first fiscal fish fist flag flake flame flank flare flash flask fleet flesh flint "
                    + "float flock flood floor flour flow fluid flute foam focal focus foggy foil folk font "
                    + "force forest forge fork form fossil found frame freeze fresh friend fringe frog front "
                    + "frost fruit fudge fuel fuse gadget gala galaxy gallery gamma garage garden garlic "
                    + "gather gauge gavel gaze gear gecko genius gentle germ ghost giant ginger giraffe "
                    + "glacier glad glance glare glass glaze gleam glide glint globe gloom glory glove glow "
                    + "glue gnome goal goat gold golf gong goose gorge gospel govern grab grace grade grain "
                    + "grand grant grape graph grasp grass grave gravel gray graze great green greet grid "
                    + "grill grip grit groove ground group grove growl guard guess guest guide guild guitar "
                    + "gulf gully gust gutter habit hail hammer hamper hand happy harbor hardy harsh harvest "
                    + "hatch haven hawk hazel heart hearth heavy hedge helix hello helmet herb hero hills "
                    + "hinge hint hive hobby hockey hollow honey honor hood hoof hook hope horn horse hotel "
                    + "hound house hover human humble humor hunt hurdle hymn icon idea igloo image impact "
                    + "inch index indigo ink inlet input insect inside invest iris iron island issue ivory "
                    + "jacket jade jaguar jazz jelly jersey jewel jockey jolly journal judge juice jumbo "
                    + "jungle junior juniper jury kayak keen kennel kettle kick kind king kiosk kite kitten "
                    + "knack knee knife knight knot koala label labor lace ladder lagoon lake lamb lamp lance "
                    + "land lantern lapel larch large larva laser lasso latch later laugh launch laurel lava "
                    + "lawn layer lead leaf league leap learn lease leash leather ledge legacy legend lemon "
                    + "lens leopard level lever light lilac lily lime linen lion liquid list lively llama "
                    + "load loaf lobby local locket lodge loft logic loop lotus loud lounge lucky lumber "
                    + "lunar lunch lung lynx lyric magic magnet maize major mammal manor mantle maple marble "
                    + "march marina market marsh mask mason match meadow medal medium melody melon memo "
                    + "mentor menu merit mesa metal meteor method metric micro middle midnight mighty mild "
                    + "mile milk mimic mineral mint mirror mist mixer model modest module molten moment "
                    + "monarch money monk month moon moral morning mosaic moss motel motion motor mound mount "
                    + "mouse movie muffin mulch mural muscle museum music mustard myth nacho napkin narrow "
                    + "nature navy nectar needle neon nerve nest neutral newt nickel niece night nimble noble "
                    + "noise nomad noon north nose notch note novel nozzle nudge nugget number nurse nutmeg "
                    + "oasis ocean octave odor offer olive omega onion onset onyx opal open opera orange "
                    + "orbit orchard order organ origin otter ounce outer oval oven owl oxide oyster pace "
                    + "pack paddle pagoda paint palace pale palm panda panel pantry paper parade parcel park "
                    + "parrot parsley party pasta pastel patch path patio patrol pattern paved peach peak "
                    + "pearl pebble pedal pelican pencil penny pepper perch perfume period permit petal "
                    + "petite phase phone photo piano picnic piece pier pigeon pilgrim pillar pilot pine pink "
                    + "pioneer pipe pirate pistol pitch pivot pixel pizza place plain plane plank plant "
                    + "plasma plate plaza pleat pledge plenty plot plow plum plume plush pocket poem point "
                    + "polar polish pollen pond pony poplar poppy porch portal poster potato pottery pouch "
                    + "pound powder power prairie praise prank prawn precise prefer press pretty price pride "
                    + "prime print prism prize probe profit prompt proof proper proud prove prune public "
                    + "pudding puffin pulley pulse pumpkin punch pupil puppy purple purse puzzle pylon "
                    + "pyramid quail quaint quake quality quarry quartz queen quench query quest queue quick "
                    + "quiet quill quilt quiver quota rabbit racer radar radio raft rail rain raise rally "
                    + "ramp ranch random range rapid rascal raven ravine razor reach react ready realm reason "
                    + "rebel recess recipe record reef refine reform region regret reign relax relay relic "
                    + "remedy remote render renew rental repair repeat reply report rescue resort result "
                    + "retina return reveal review reward rhino rhyme rhythm ribbon rice ridge rifle right "
                    + "rigid ring rinse ripe ripple rise ritual river road roast robin robot rocket rodeo "
                    + "rogue roller roof room roost root rope rose rotate rough round route rover royal "
                    + "rubber ruby rudder rugged ruler rumble runner rural rust sable sacred saddle safari "
                    + "sage sail salad salmon salon salt sample sand sapphire sash satin sauce savor scale "
                    + "scarf scene scent scholar school scope score scout scrap screen script scroll sculpt "
                    + "seal search season second sector sedan seed seek segment select senate sense sepia "
                    + "serene serve settle seven shade shadow shaft shale shape share shark sharp shed sheep "
                    + "sheet shelf shell shield shift shine ship shirt shoal shock shore short shovel shower "
                    + "shrimp shrub shuttle sierra sift signal silent silk silver simple singer siren sister "
                    + "sketch skill skirt skull slate sled sleek sleep slice slide slope slot small smart "
                    + "smile smoke smooth snack snail snake snap sneak snow soap social socket sodium soft "
                    + "solar solid solve sonar sonnet soothe sorbet sorrel sound soup south space spade spark "
                    + "speak spear speck speed spell sphere spice spider spike spine spiral spirit splash "
                    + "split spoke sponge spoon sport spot spout spray spring sprout spruce square squash "
                    + "squid stable stack staff stage stair stamp stand staple star state statue steady steam "
                    + "steel stem step stereo stick still stitch stock stone stool storm story stove strand "
                    + "straw stream street stretch strike string strive strong studio study stump style sugar "
                    + "suite sultan summer summit sunny sunset super supply surf surge survey swamp swan "
                    + "sweater sweep sweet swift swim swirl switch sword symbol syrup system table tablet "
                    + "tackle tactic tailor talent tally talon tandem tangle tanker tape target tavern teach "
                    + "teal team tempo tender tennis tent terrain thank thaw theater theme thermal thicket "
                    + "thimble thistle thorn thread thrive throne thumb thunder ticket tidal tiger tile "
                    + "timber timer tinsel tiny tissue title toast today toffee toggle token tomato tone "
                    + "tonic tool tooth topaz topic torch total totem toucan touch tough tower town trace "
                    + "track trade trail train tram trance transit trap travel tray treat treble tree trend "
                    + "trial tribe trick trim trio trolley trophy tropic trout truck true trunk trust truth "
                    + "tulip tumble tundra tune tunnel turbo turkey turret turtle tutor twig twin twist ultra "
                    + "umber umbrella uncle under unify union unique unit unity update uphill upland upper "
                    + "upset urban urchin usage useful usher utensil vacant valley value valve vanilla vapor "
                    + "vault vector velvet vendor venture venue verse vessel vibrant victor video view vigor "
                    + "villa vine vinyl violet viper virtue vision visit vital vivid vocal voice volume vote "
                    + "voyage waffle wagon walnut walrus wander warden warm warp wasp watch water wave weave "
                    + "wedge weekly weight welcome whale wharf wheat wheel whisk white widget willow window "
                    + "winter wire wisdom witty wizard wolf wonder wood wool world worth woven wrench wrist "
                    + "write yacht yard yarn yeast yellow yield yogurt young zebra zenith zephyr zero zigzag "
                    + "zinc zone ").trim().split(" +");

    /** Entropy each generated passphrase word contributes. */
    public static final double BITS_PER_WORD = Math.log(1492) / Math.log(2);

    public static class Options {
        public int length = 20;
        public boolean useLower = true;
        public boolean useUpper = true;
        public boolean useDigits = true;
        public boolean useSymbols = true;
        /** Exclude characters that look alike: l/1/I, 0/O. */
        public boolean avoidAmbiguous = true;
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate(Options options) {
        List<String> pools = new ArrayList<>();
        if (options.useLower) pools.add(options.avoidAmbiguous ? LOWER : LOWER_FULL);
        if (options.useUpper) pools.add(options.avoidAmbiguous ? UPPER : UPPER_FULL);
        if (options.useDigits) pools.add(options.avoidAmbiguous ? DIGITS : DIGITS_FULL);
        if (options.useSymbols) pools.add(SYMBOLS);

        if (pools.isEmpty()) pools.add(LOWER_FULL);

        int length = Math.max(options.length, pools.size());

        StringBuilder combined = new StringBuilder();
        for (String pool : pools) combined.append(pool);

        List<Character> chars = new ArrayList<>(length);

        // One guaranteed character from each selected class.
        for (String pool : pools) {
            chars.add(pool.charAt(RANDOM.nextInt(pool.length())));
        }
        // Fill the rest from the combined pool.
        while (chars.size() < length) {
            chars.add(combined.charAt(RANDOM.nextInt(combined.length())));
        }

        // Fisher-Yates via Collections, seeded with SecureRandom.
        Collections.shuffle(chars, RANDOM);

        StringBuilder out = new StringBuilder(length);
        for (char c : chars) out.append(c);
        return out.toString();
    }

    /**
     * Diceware-style passphrase: "timber-quartz-hollow-nectar-thistle-42".
     *
     * Words are sampled without replacement. Drawing with replacement would let
     * "pillar-pillar-lantern-yarrow" come out, which looks broken and throws away
     * entropy on the duplicate.
     */
    public static String generatePassphrase(int wordCount, boolean appendNumber) {
        int count = Math.min(Math.max(4, wordCount), WORDS.length);

        List<String> picked = new ArrayList<>(count);
        while (picked.size() < count) {
            String candidate = WORDS[RANDOM.nextInt(WORDS.length)];
            if (!picked.contains(candidate)) picked.add(candidate);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < picked.size(); i++) {
            if (i > 0) sb.append('-');
            sb.append(picked.get(i));
        }
        if (appendNumber) sb.append('-').append(10 + RANDOM.nextInt(90));
        return sb.toString();
    }

    private PasswordGenerator() { }
}
