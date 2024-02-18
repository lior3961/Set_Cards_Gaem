package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime;

    private long startLoopTime;

    private long timePassed;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.startLoopTime = 0;
        this.timePassed = 0;
        this.terminate = false;
        this.reshuffleTime = env.config.turnTimeoutMillis;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (Player p : this.players) {
            Thread playerThread = new Thread(p);
            playerThread.start();
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            startLoopTime = System.currentTimeMillis();
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    private void timerLoop() {

        while (!terminate && timePassed < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks if cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {

        while (!table.getPlayerWith3Tokens().isEmpty()) {
            Player p = this.findPlayer(table.getPlayerWith3Tokens().poll());
            if (env.util.testSet(p.getTokens())) {
                for (int i = 0; i < p.getTokens().length; i++) {
                    int card = p.getTokens()[i];
                    removeTokens(table.cardToSlot[card]);
                    env.ui.removeCard(table.cardToSlot[card]);
                    table.removeCard(table.cardToSlot[card]);
                }
                updateTimerDisplay(true);
                p.resetPlayerToken();
                p.point();
                updatePlayersWith3Tokens();
            } else {
                p.resetPlayerToken();
                p.penalty();

            }

        }

    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        int numOfCards = table.countCards();
        if (numOfCards == 0) // Checks if the table needs to be renewed
        {
            synchronized (table) // No player can touch the table in this case
            {
                shuffleDeck();
                for (int i = 0; i < table.slotToCard.length; i++) {
                    table.placeCard(takeCard(), i); // place card in table in slot i
                    env.ui.placeCard(table.slotToCard[i], i); // UI: show card on table
                }
            }
        } else {
            for (int i = 0; i < table.slotToCard.length && !deck.isEmpty(); i++) {
                if (table.slotToCard[i] == null) {
                    int card = takeCard();
                    table.placeCard(card, i);
                    env.ui.placeCard(card, i);
                }
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset) {
            timePassed = 0;
            startLoopTime = System.currentTimeMillis();
            env.ui.setCountdown(reshuffleTime, false); // starts the countDown from 60 seconds
        } else {
            timePassed = System.currentTimeMillis() - startLoopTime;
            if (reshuffleTime - timePassed < env.config.turnTimeoutWarningMillis) // timer will be red if there are less
                                                                                  // then 10 seconds left
            {
                env.ui.setCountdown(reshuffleTime - timePassed, true);
            } else {
                env.ui.setCountdown(reshuffleTime - timePassed, false);
            }
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for (Integer card : table.slotToCard) {

            deck.add(card);
            env.ui.removeCard(table.cardToSlot[card]);
            this.table.removeCard(table.cardToSlot[card]);
        }
        this.resetTokens();
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
    }

    /**
     * shuffle the dealer's deck
     */
    private void shuffleDeck() {
        Collections.shuffle(this.deck);
    }

    /**
     * takes the first card from the deck
     */
    private int takeCard() {
        return this.deck.remove(0);
    }

    private void resetTokens() // When we deal the table anew
    {
        env.ui.removeTokens();
        for (Player p : players) {
            p.resetPlayerToken();
        }
    }

    private Player findPlayer(int id) {
        for (Player p : this.players) {
            if (p.id == id)
                return p;
        }
        return null;
    }

    private void removeTokens(int slot)
    // deletes the tokens from the array of each player that put a token in the
    // relevant slot
    {
        env.ui.removeTokens(slot);
        int card = table.slotToCard[slot];
        for (Player p : players) {
            p.removeTokenFromCard(card);
        }
    }

    public void updatePlayersWith3Tokens() {
        int size = table.getPlayerWith3Tokens().size();
        for (int i = 0; i < size; i++) {
            Player p = findPlayer(table.getPlayerWith3Tokens().poll());
            if (p.getCountTokens() == 3) {
                table.addPlayerWith3Tokens(p.id);
            }
        }
    }
}
