package bguspl.set.ex;

import java.util.LinkedList;
import java.util.Queue;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    private int[] tokens; //tokens[i] is the card that the token is placed on, -1 if it has not been placed yet

    private int countTokens; // how much tokens have been placed already
    
    /**
     * Player actions queue.
     */
private Queue<Integer> actions;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.actions = new LinkedList<Integer>();
        this.tokens = new int[3];
        this.countTokens = 0;
        tokens[0] = -1;
        tokens[1] = -1;
        tokens[2] = -1;
        
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();

        while (!terminate) {
            // TODO implement main player loop
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                keyPressed(countTokens);
                try {
                    synchronized (this) { wait(); }
                } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        boolean toRemove = false; //did we find a token to remove?
        int x = -1;
        for(int i = 0; i < tokens.length && !toRemove; i++)
        {
            if(tokens[i] == table.slotToCard[slot]) // the player pressed on a token he already placed
            {
                table.removeToken(this.id, slot);
                tokens[i] = -1;
                countTokens--;
                toRemove = true;
            }
            else if(tokens[i] == -1) //save an available place in the tokens array
            {
                x = i;
            }
        }
        if(!toRemove && x != -1) // place the token and save it in the player's array
        {
            tokens[x] = table.slotToCard[slot];
            table.placeToken(this.id, slot);
            countTokens++;
            if(countTokens == 3)
            {
                table.addPlayerWith3Tokens(this.id);
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        env.ui.setScore(this.id, ++this.score);
    // TODO
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        //TODO
    }

    public int score() {
        return score;
    }
    public int[] getTokens(){
        return this.tokens;
    }
    
    public int getCountTokens(){
        return this.countTokens;
    }

    public void resetPlayerToken()
    {
        for(int i = 0 ; i < this.tokens.length ; i++)
        {
            if(tokens[i] != -1)
            {
                env.ui.removeToken(this.id, table.cardToSlot[tokens[i]]);
                this.tokens[i] = -1;
            }
        }
        this.countTokens = 0;
    } 

    public int getIndexOfToken(int card) // return index of card in my tokens array if exist, -1 otherwise
    {
        for(int i = 0 ;  i < this.tokens.length ; i++)
        {
            if(this.tokens[i] == card)
            {
                return i; 
            }
        }
        return -1;
    }

    public void removeTokenFromCard(int card) //deletes the token of the player from his array if needed
    {
        int index = getIndexOfToken(card);
        if(index != -1)
        {
            this.countTokens--;
            this.tokens[index] = -1;
        }
    }
    // marina tokens stayed after legal set of meni 
    // the timer didnt reset
}
