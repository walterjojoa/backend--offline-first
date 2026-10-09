package com.lacocha.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lacocha.backend.dto.Parameters.FactorResponse;
import com.lacocha.backend.dto.Parameters.ParametersResponse;
import com.lacocha.backend.dto.Parameters.RangeResponse;
import com.lacocha.backend.dto.Parameters.RateResponse;
import com.lacocha.backend.repository.FeedingRateRepository;
import com.lacocha.backend.repository.RangeParameterRepository;
import com.lacocha.backend.repository.TemperatureFactorRepository;

/**
 * Loads the expert system's numbers from the database. They change very rarely and are needed for
 * every reading (a push can bring 500), so the loaded {@link Rules} is kept in memory.
 * Two threads loading it at the same time read the same data, so no locking is needed.
 */
@Service
public class RuleParameters {

    private final RangeParameterRepository ranges;
    private final FeedingRateRepository rates;
    private final TemperatureFactorRepository factors;
    private volatile Rules cached;

    public RuleParameters(RangeParameterRepository ranges, FeedingRateRepository rates,
            TemperatureFactorRepository factors) {
        this.ranges = ranges;
        this.rates = rates;
        this.factors = factors;
    }

    @Transactional(readOnly = true)
    public Rules current() {
        Rules rules = cached;
        if (rules == null) {
            rules = load();
            cached = rules;
        }
        return rules;
    }

    /** Reads the tables again on the next use, after a threshold was changed in the database. */
    public void reload() {
        cached = null;
    }

    private Rules load() {
        List<Rules.Range> rangeList = ranges.findAllByOrderByVariableAsc().stream()
                .map(p -> new Rules.Range(p.getVariable(), p.getLabel(), p.isFeminine(), p.getUnit(),
                        p.getOptimalMin(), Rules.orInfinity(p.getOptimalMax()),
                        p.getCriticalMin(), Rules.orInfinity(p.getCriticalMax())))
                .toList();
        List<Rules.Step> rateList = rates.findAllByOrderByPositionAsc().stream()
                .map(r -> new Rules.Step(Rules.orInfinity(r.getUpToWeightG()), r.getRatePct()))
                .toList();
        List<Rules.Step> factorList = factors.findAllByOrderByPositionAsc().stream()
                .map(f -> new Rules.Step(Rules.orInfinity(f.getUpToTempC()), f.getFactor()))
                .toList();
        return new Rules(rangeList, rateList, factorList);
    }

    /** For GET /api/parametros: the panel can warn while a source still says "sin citar". */
    @Transactional(readOnly = true)
    public ParametersResponse response() {
        return new ParametersResponse(
                ranges.findAllByOrderByVariableAsc().stream().map(RangeResponse::from).toList(),
                rates.findAllByOrderByPositionAsc().stream().map(RateResponse::from).toList(),
                factors.findAllByOrderByPositionAsc().stream().map(FactorResponse::from).toList());
    }
}
