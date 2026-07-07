package dev.pioruocco.wacchat.bot;

public final class BotConstants {

    /** Fixed id, never issued by Keycloak — User.id has no @GeneratedValue, so this is
     *  set manually instead of coming from a JWT sub claim like every other user. */
    public static final String ARNO_USER_ID = "00000000-0000-0000-0000-000000000001";
    public static final String ARNO_USERNAME = "arno";
    public static final String ARNO_FIRST_NAME = "Arno";
    public static final String ARNO_LAST_NAME = "AI";
    public static final String ARNO_EMAIL = "arno@wacchat.bot";

    public static final String WELCOME_MESSAGE =
            "Ciao! Sono Arno AI 🐦 il tuo assistente virtuale su WacChat. "
                    + "Sono qui se ti va di scambiare due chiacchiere o hai bisogno di una mano. Scrivimi pure! "
                    + "Ps: questa è un'app sviluppata per hobby, dopo 2 mesi tutti i dati dell'utente corrente verranno cancellati.";

    private BotConstants() {
    }
}
