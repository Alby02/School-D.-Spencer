package it.uniupo.macchinetta;

public class Coins {
    private static Coins instance = null;
    private int credit = 0;
    private int monetine = 0;
    private int [] coins = {0,0,0,0,0,0};
    private Coins() {
    }

    public static synchronized Coins getInstance() {
        if (instance == null) {
            instance = new Coins();
        }
        return instance;
    }
    public synchronized int getCredito() {
        return credit;
    }

    public synchronized int getMonetine() {
        return monetine;
    }

    public synchronized void insert5Cents() {
        monetine ++;
        credit += 5;
        coins[0]++;
    }

    public synchronized void insert10Cents() {
        monetine ++;
        credit += 10;
        coins[1]++;
    }

    public synchronized void insert20Cents() {
        monetine ++;
        credit += 20;
        coins[2]++;
    }

    public synchronized void insert50Cents() {
        monetine ++;
        credit += 50;
        coins[3]++;
    }

    public synchronized void insert1Euro() {
        monetine ++;
        credit += 100;
        coins[4]++;
    }

    public synchronized void insert2Euros() {
        monetine ++;
        credit += 200;
        coins[5]++;
    }

    public synchronized void emptyCoins() {
        credit -= coins[0]*5 + coins[1]*10 + coins[2]*20 + coins[3]*50 + coins[4]*100 + coins[5]*200;
        monetine = 0;
        coins = new int[]{0, 0, 0, 0, 0, 0};
    }

    public synchronized int subtractAndDischarge(int amount) {
        int moneteTemp = monetine;
        credit -= amount;
        monetine = 0;
        coins = new int[]{0,0,0,0,0,0};

        return moneteTemp;
    }

    public synchronized void changeCredit(int amount) {
        credit -= amount;
    }
}
