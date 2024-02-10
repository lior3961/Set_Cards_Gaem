package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Arrays;

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
    private long reshuffleTime = 60000;


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
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
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
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
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
     * check if there are no sets left in all remaining cards (including the table).
     * 
     * @return true iff there is no sets left in the game.
     */
    private boolean shouldFinishWhileLoop() {
        List<Integer> allRemainingCards = new LinkedList<>();
        allRemainingCards.addAll(this.deck); // adds deck cards into list
        allRemainingCards.addAll(Arrays.asList(this.table.slotToCard)); // adds table cards into list
        return env.util.findSets(allRemainingCards, 1).size() == 0; 
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        



    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() { 
        if(!shouldFinishWhileLoop())
        {
            int numOfCards = table.countCards();          
            if(numOfCards == 0) //Check if the table needs to be renewed
            {
                synchronized(table)
                {
                    shuffleDeck();
                    for(int i = 0; i < table.slotToCard.length; i++)
                    {   
                        table.placeCard(takeCard(), i); // place card in table in slot i
                        env.ui.placeCard(table.slotToCard[i], i); // UI: show card on table
                    } 
                }
            }             
            else 
            {
                for(int i = 0 ; i < table.slotToCard.length && !deck.isEmpty() ; i++)
                {
                    if(table.slotToCard[i] == null)
                    {
                        int card = takeCard();
                        table.placeCard(card, i);
                        env.ui.placeCard(card, i);
                    }
                }      
            }
        
        }
        else
        {

            terminate();   
        }      
    }


    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if(reset)
        {
            timePassed = 0;
            env.ui.setCountdown(reshuffleTime ,false); //starts the countDown from 60 seconds
        }
        else
        {
            timePassed = System.currentTimeMillis() - startLoopTime;  
            if(reshuffleTime - timePassed < 10000) // timer will be red if there are less then 10 seconds left
            {
                env.ui.setCountdown(reshuffleTime - timePassed ,true);   
            }  
            else
            {
                env.ui.setCountdown(reshuffleTime - timePassed ,false);   
            }
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
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
}
