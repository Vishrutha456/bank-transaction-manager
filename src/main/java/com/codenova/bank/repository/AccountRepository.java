package com.codenova.bank.repository;

import com.codenova.bank.exception.AccountNotFoundException;
import com.codenova.bank.model.Account;

import java.util.Collection;

/** Storage abstraction so the service layer never depends on a concrete file format. */
public interface AccountRepository {

    void add(Account account);

    Account findById(String accountId) throws AccountNotFoundException;

    boolean exists(String accountId);

    Collection<Account> findAll();

    /** Highest transaction sequence number stored, or 0 if none. */
    long lastSequence();

    /** Persists the full current state. */
    void save();
}
