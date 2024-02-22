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
        terminate();
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
        for (Player p : this.players) {
            p.terminate();
            p.getPlayerThread().interrupt();
        }
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
            try {
                int playerId = this.table.getPlayerWith3Tokens().take(); // the player id
                int[] playerSet = this.table.getPlayerSet(playerId); // array of the player cards set
                if (env.util.testSet(playerSet))
                {
                    for (int i = 0; i < playerSet.length; i++)
                    {
                        int card = playerSet[i];
                        env.logger.info("testing player set card : " + card);
                        env.logger.info("testing card to slot array : " + table.cardToSlot[card]);
                        table.removeCard(table.cardToSlot[card]);  //synchronized in table   
                        table.resetAllTokens(table.cardToSlot[card]);                       
                    }
                    updateTimerDisplay(true);
                    findPlayer(playerId).point();
                }
                else //The set is illegal -> player is penalized
                {                   
                    findPlayer(playerId).penalty();
                }
            } catch (InterruptedException e) {
                env.logger.info("thread " + Thread.currentThread().getName() + " interrupted.");
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
            shuffleDeck();
            for (int i = 0; i < table.slotToCard.length; i++) {
                int card = takeCard();
                table.placeCard(card, i); // place card in table in slot i
            }
        }
        else {
            for (int i = 0; i < table.slotToCard.length && !deck.isEmpty(); i++) {
                if (table.slotToCard[i] == null) {
                    int card = takeCard();
                    table.placeCard(card, i);
                }
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        try {
            synchronized (this.table.getPlayerWith3Tokens()) { 
                this.table.getPlayerWith3Tokens().wait(1000);
            }
        } catch (InterruptedException e) {
            env.logger.info("thread " + Thread.currentThread().getName() + " interrupted.");
        }
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset)
        {
            timePassed = 0;
            startLoopTime = System.currentTimeMillis();
            env.ui.setCountdown(reshuffleTime, false); // starts the countDown from 60 seconds
        } 
        else
        {
            timePassed = System.currentTimeMillis() - startLoopTime;
            env.ui.setCountdown(reshuffleTime - timePassed, false);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        for (int i = 0 ; i < this.table.slotToCard.length ; i++) {
            Integer card = this.table.slotToCard[i];
            if(card != null)
            {
                deck.add(card);
                this.table.removeCard(i);
                this.table.resetAllTokens(i);
                
            }
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        LinkedList<Integer> winners = new LinkedList<Integer>();
        int maxPoints = 0;
        for (Player p : this.players) {
            if (p.score() > maxPoints) {
                winners.clear();
                winners.add(p.id);
                maxPoints = p.score();
            } else {
                if (p.score() == maxPoints) {
                    winners.add(p.id);
                }
            }
        }
        int[] winnersArray = winners.stream().mapToInt(Integer::intValue).toArray();
        env.ui.announceWinner(winnersArray);
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

    private Player findPlayer(int id) {
        for (Player p : this.players) {
            if (p.id == id)
                return p;
        }
        return null;
    }

}
