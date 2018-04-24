// AUTOGENERATED FILE - DO NOT MODIFY!
// This file generated by Djinni from wallet.djinni

package co.ledger.core;

import java.util.concurrent.atomic.AtomicBoolean;

/**Class representing an account */
public abstract class Account {
    /**
     *Key of the synchronization duration time in the synchronize event payload.
     *The value is stored in a int 64 time expressed in miliseconds.
     */
    public static final String EV_SYNC_DURATION_MS = "EV_SYNC_DURATION_MS";

    /**Key of the synchronization error code. The code is a stringified version of the value in the ErrorCode enum. */
    public static final String EV_SYNC_ERROR_CODE = "EV_SYNC_ERROR_CODE";

    /**Key of the synchronization error message. The message is stored as a string. */
    public static final String EV_SYNC_ERROR_MESSAGE = "EV_SYNC_ERROR_MESSAGE";

    /**TODO */
    public static final String EV_NEW_BLOCK_CURRENCY_NAME = "EV_NEW_BLOCK_CURRENCY_NAME";

    public static final String EV_NEW_BLOCK_HASH = "EV_NEW_BLOCK_HASH";

    public static final String EV_NEW_BLOCK_HEIGHT = "EV_NEW_BLOCK_HEIGHT";

    /**TODO */
    public static final String EV_NEW_OP_WALLET_NAME = "EV_NEW_OP_WALLET_NAME";

    public static final String EV_NEW_OP_ACCOUNT_INDEX = "EV_NEW_OP_ACCOUNT_INDEX";

    public static final String EV_NEW_OP_UID = "EV_NEW_OP_UID";

    /**
     *Get index of account in user's wallet
     *32 bits integer
     */
    public abstract int getIndex();

    /**TODO */
    public abstract OperationQuery queryOperations();

    /**
     *Get balance of account
     *@param callback, if getBalacne, Callback returning an Amount object which represents account's balance
     */
    public abstract void getBalance(AmountCallback callback);

    /**
     *Get synchronization status of account
     *@return bool
     */
    public abstract boolean isSynchronizing();

    /**
     *Start synchronization of account
     *@return EventBus, handler will be notified of synchronization outcome
     */
    public abstract EventBus synchronize();

    /**
     *Return account's preferences
     *@return Preferences object
     */
    public abstract Preferences getPreferences();

    /**
     *Return account's logger which provides all needed (e.g. database) logs
     *@return Logger Object
     */
    public abstract Logger getLogger();

    /**
     *Return preferences of specific operation
     *@param uid, string of operation id
     *@return Preferences
     *Return operation for a specific operation
     *@param uid, string of operation id
     */
    public abstract Preferences getOperationPreferences(String uid);

    /**
     * asBitcoinLikeAccount(): Callback<BitcoinLikeAccount>;
     * asEthereumLikeAccount(): Callback<EthereumLikeAccount>;
     * asRippleLikeAccount(): Callback<RippleLikeAccount>;
     *Check if account is a Bitcoin one
     *@return bool
     */
    public abstract boolean isInstanceOfBitcoinLikeAccount();

    /**
     *Check if account is an Ethereum one
     *@return bool
     */
    public abstract boolean isInstanceOfEthereumLikeAccount();

    /**
     *Check if account is a Ripple one
     *@return bool
     */
    public abstract boolean isInstanceOfRippleLikeAccount();

    /**TODO */
    public abstract void getFreshPublicAddresses(StringListCallback callback);

    /**
     *Get type of wallet to which account belongs
     *@return WalletType object
     */
    public abstract WalletType getWalletType();

    /**
     *Get event bus through which account is notified on synchronization status
     *@return EventBus object
     */
    public abstract EventBus getEventBus();

    /**Start observing blockchain on which account synchronizes and send/receive transactions */
    public abstract void startBlockchainObservation();

    /**Stop observing blockchain */
    public abstract void stopBlockchainObservation();

    /**
     *Get account's observation status
     *@return boolean
     */
    public abstract boolean isObservingBlockchain();

    /**
     *Get Last block of blockchain on which account operates
     *@param callback, Callback returning, if getLastBlock succeeds, a Block object
     */
    public abstract void getLastBlock(BlockCallback callback);

    public abstract BitcoinLikeAccount asBitcoinLikeAccount();

    private static final class CppProxy extends Account
    {
        private final long nativeRef;
        private final AtomicBoolean destroyed = new AtomicBoolean(false);

        private CppProxy(long nativeRef)
        {
            if (nativeRef == 0) throw new RuntimeException("nativeRef is zero");
            this.nativeRef = nativeRef;
        }

        private native void nativeDestroy(long nativeRef);
        public void destroy()
        {
            boolean destroyed = this.destroyed.getAndSet(true);
            if (!destroyed) nativeDestroy(this.nativeRef);
        }
        protected void finalize() throws java.lang.Throwable
        {
            destroy();
            super.finalize();
        }

        @Override
        public int getIndex()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_getIndex(this.nativeRef);
        }
        private native int native_getIndex(long _nativeRef);

        @Override
        public OperationQuery queryOperations()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_queryOperations(this.nativeRef);
        }
        private native OperationQuery native_queryOperations(long _nativeRef);

        @Override
        public void getBalance(AmountCallback callback)
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            native_getBalance(this.nativeRef, callback);
        }
        private native void native_getBalance(long _nativeRef, AmountCallback callback);

        @Override
        public boolean isSynchronizing()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_isSynchronizing(this.nativeRef);
        }
        private native boolean native_isSynchronizing(long _nativeRef);

        @Override
        public EventBus synchronize()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_synchronize(this.nativeRef);
        }
        private native EventBus native_synchronize(long _nativeRef);

        @Override
        public Preferences getPreferences()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_getPreferences(this.nativeRef);
        }
        private native Preferences native_getPreferences(long _nativeRef);

        @Override
        public Logger getLogger()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_getLogger(this.nativeRef);
        }
        private native Logger native_getLogger(long _nativeRef);

        @Override
        public Preferences getOperationPreferences(String uid)
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_getOperationPreferences(this.nativeRef, uid);
        }
        private native Preferences native_getOperationPreferences(long _nativeRef, String uid);

        @Override
        public boolean isInstanceOfBitcoinLikeAccount()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_isInstanceOfBitcoinLikeAccount(this.nativeRef);
        }
        private native boolean native_isInstanceOfBitcoinLikeAccount(long _nativeRef);

        @Override
        public boolean isInstanceOfEthereumLikeAccount()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_isInstanceOfEthereumLikeAccount(this.nativeRef);
        }
        private native boolean native_isInstanceOfEthereumLikeAccount(long _nativeRef);

        @Override
        public boolean isInstanceOfRippleLikeAccount()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_isInstanceOfRippleLikeAccount(this.nativeRef);
        }
        private native boolean native_isInstanceOfRippleLikeAccount(long _nativeRef);

        @Override
        public void getFreshPublicAddresses(StringListCallback callback)
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            native_getFreshPublicAddresses(this.nativeRef, callback);
        }
        private native void native_getFreshPublicAddresses(long _nativeRef, StringListCallback callback);

        @Override
        public WalletType getWalletType()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_getWalletType(this.nativeRef);
        }
        private native WalletType native_getWalletType(long _nativeRef);

        @Override
        public EventBus getEventBus()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_getEventBus(this.nativeRef);
        }
        private native EventBus native_getEventBus(long _nativeRef);

        @Override
        public void startBlockchainObservation()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            native_startBlockchainObservation(this.nativeRef);
        }
        private native void native_startBlockchainObservation(long _nativeRef);

        @Override
        public void stopBlockchainObservation()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            native_stopBlockchainObservation(this.nativeRef);
        }
        private native void native_stopBlockchainObservation(long _nativeRef);

        @Override
        public boolean isObservingBlockchain()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_isObservingBlockchain(this.nativeRef);
        }
        private native boolean native_isObservingBlockchain(long _nativeRef);

        @Override
        public void getLastBlock(BlockCallback callback)
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            native_getLastBlock(this.nativeRef, callback);
        }
        private native void native_getLastBlock(long _nativeRef, BlockCallback callback);

        @Override
        public BitcoinLikeAccount asBitcoinLikeAccount()
        {
            assert !this.destroyed.get() : "trying to use a destroyed object";
            return native_asBitcoinLikeAccount(this.nativeRef);
        }
        private native BitcoinLikeAccount native_asBitcoinLikeAccount(long _nativeRef);
    }
}
