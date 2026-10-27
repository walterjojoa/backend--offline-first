package com.lacocha.backend.service;

import com.lacocha.backend.model.Finding;

/**
 * Where a finding goes once it is stored, so it reaches the screen without anybody pressing
 * anything.
 *
 * It is a seam and not a concrete class because the analysis has no business knowing how a browser
 * is reached. The real implementation lives in the event handler (see EventStream); a deployment
 * that does not want live updates can leave {@link #silent()} in place and everything else keeps
 * working, because the finding is already stored by the time this is called.
 */
public interface FindingPublisher {

    void publish(Finding finding);

    /** Stores and says nothing. The app will see the finding the next time it asks. */
    static FindingPublisher silent() {
        return finding -> {
        };
    }
}
