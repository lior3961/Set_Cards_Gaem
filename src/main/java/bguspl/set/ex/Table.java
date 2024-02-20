package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    /**
     * An array representing the grid of the table and the tokens placed inside by
     * the ID of the player
     */
    private LinkedList<Integer>[] tokensByPlayersID;

    /**
     * AA BlockingQueue of
     */
    private BlockingQueue<Integer> playersWith3Tokens;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if
     *                   none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if
     *                   none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;
        this.tokensByPlayersID = new LinkedList[slotToCard.length];
        for (int i = 0; i < tokensByPlayersID.length; i++) {
            this.tokensByPlayersID[i] = new LinkedList<Integer>();
        }
        playersWith3Tokens = new LinkedBlockingQueue<Integer>();
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the
     * table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted()
                    .collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(
                    sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * 
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }

        cardToSlot[card] = slot;
        slotToCard[slot] = card;
        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * 
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {
        }
        env.ui.removeCard(slot);
        slotToCard[slot] = null;
    }

    /**
     * Places a player token on a grid slot.
     * 
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        env.ui.placeToken(player, slot);
        this.tokensByPlayersID[slot].add(player);
    }

    /**
     * Removes a token of a player from a grid slot.
     * 
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        if (tokensByPlayersID[slot].contains(player)) {
            env.ui.removeToken(player, slot);
            tokensByPlayersID[slot].remove((Object)player);
            env.logger.info("Remove token for player " + player);
            if (this.playersWith3Tokens.contains(player)) {
                this.playersWith3Tokens.remove(player);
            }
            return true;
        }
        return false;
    }

    public int[] getPlayerSet(int player) {
        int[] playerSet = new int[3];
        int card = 0;
        for (int i = 0; i < this.tokensByPlayersID.length; i++) {
            if (this.tokensByPlayersID[i].contains(player)) {
                playerSet[card] = slotToCard[i];
                card++;
            }
        }
        return playerSet;
    }

    public BlockingQueue<Integer> getPlayerWith3Tokens() {
        return playersWith3Tokens;
    }

    public void addPlayerWith3Tokens(int player) {
        this.playersWith3Tokens.add(player);
        synchronized (this.playersWith3Tokens) {
            this.playersWith3Tokens.notifyAll();
        }
    }

    public LinkedList<Integer>[] getTokensByPlayersID() {
        return this.tokensByPlayersID;
    }

    // reset all tokens from table
    public void resetAllTokens() {
        for (int i = 0; i < this.tokensByPlayersID.length; i++) {
            for (int playerID : this.tokensByPlayersID[i]) {
                this.removeToken(playerID, i);
            }
        }
    }

    // get player tokens count
    public int getCountTokensByPlayer(int id) {
        int count = 0;
        for (LinkedList<Integer> lst : this.getTokensByPlayersID()) {
            if (lst.contains(id)) {
                count++;
            }
        }
        return count;
    }
}
