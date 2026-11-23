package com.trading.portfolioservice.service;

import com.trading.portfolioservice.client.MarketDataClient;
import com.trading.portfolioservice.entity.Holding;
import com.trading.portfolioservice.repository.HoldingRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@AllArgsConstructor
public class PortfolioService {


    private HoldingRepository portfolioRepository;
    private MarketDataClient marketDataClient;

    /*
     * Builds the user's portfolio view: what they own, what it's worth now, and their profit/loss.
     *
     * Worked example used in the comments below (user "ahmed-123"):
     *
     *   Trades he made:
     *     BUY  10 AAPL at $150  -> paid $1,500
     *     BUY  10 AAPL at $170  -> paid $1,700
     *     BUY   5 TSLA at $200  -> paid $1,000
     *     SELL  5 AAPL at $180  -> got back $900
     *
     *   So the holdings table (what he PAID) now has:
     *     | symbol | quantity | averageBuyPrice | totalInvested |
     *     | AAPL   |    15    |     160.00      |   2,400.00    |   (15 x 160)
     *     | TSLA   |     5    |     200.00      |   1,000.00    |   ( 5 x 200)
     *
     *   market-data-service says today's prices are (what it's WORTH):
     *     AAPL = $175.00, TSLA = $190.00
     *
     *   Result:
     *     | symbol | invested | currentValue | pnl     | pnlPercent |
     *     | AAPL   | 2,400.00 |   2,625.00   | +225.00 |   +9.38 %  |
     *     | TSLA   | 1,000.00 |     950.00   |  -50.00 |   -5.00 %  |
     *     | TOTAL  | 3,400.00 |   3,575.00   | +175.00 |   +5.15 %  |
     */
    public Map<String, Object> getPortfolio(String userId) {

        // Every stock the user currently owns, one row per symbol.
        // Example: [AAPL (qty 15, avg 160, invested 2400), TSLA (qty 5, avg 200, invested 1000)]
        List<Holding> userHoldings = portfolioRepository.findByUserId(userId);

        List<Map<String, Object>> portfolioItems = new ArrayList<>();


        // Running totals for the whole portfolio, summed across all holdings in the loop.
        // Example after the loop: totalInvested = 2400 + 1000 = 3,400.00
        //                         currentValue  = 2625 +  950 = 3,575.00
        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal currentValue = BigDecimal.ZERO;

        for (Holding holding : userHoldings) {
            try {
                // Get The Current Market Price
                // Asks market-data-service for today's price; the holdings table only knows what he paid.
                // Example: getStockPrice("AAPL") -> {"symbol":"AAPL", "price":175.00, ...}
                Map<String, Object> priceData = marketDataClient.getStockPrice(holding.getSymbol());

                // Example: AAPL -> 175.00   |   TSLA -> 190.00
                BigDecimal currentValueOfPrice = new BigDecimal(priceData.get("price").toString());


                // Value now = current price x quantity owned  (what he'd get if he sold everything today)
                // Example: AAPL -> 175.00 x 15 = 2,625.00   |   TSLA -> 190.00 x 5 = 950.00
                BigDecimal holdingValues = currentValueOfPrice.multiply(BigDecimal.valueOf(holding.getQuantity()));


                // Invested = what he paid for the shares he still owns  (quantity x averageBuyPrice)
                // Example: AAPL -> 15 x 160.00 = 2,400.00   |   TSLA -> 5 x 200.00 = 1,000.00
                BigDecimal invested = holding.getTotalInvested();

                // Profit / Loss (unrealized) = value now - invested
                // Positive = profit, negative = loss.
                // Example: AAPL -> 2,625.00 - 2,400.00 = +225.00 (profit)
                //          TSLA ->   950.00 - 1,000.00 =  -50.00 (loss)
                BigDecimal pnl = holdingValues.subtract(invested);

                // Profit / Loss % = (pnl / invested) x 100
                //   divide(..., 4, HALF_UP) keeps 4 decimals so the % is still accurate after x100,
                //   then setScale(2) rounds the final % to 2 decimals.
                // Example: AAPL -> 225.00 / 2,400.00 = 0.09375 -> 0.0938 (4 dp) -> x100 = 9.38 %
                //          TSLA -> -50.00 / 1,000.00 = -0.0500             -> x100 = -5.00 %
                BigDecimal pnlPercent = pnl.divide(invested, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale((int) 2., RoundingMode.HALF_UP);

                // Example of the item for AAPL once filled in:
                //   symbol=AAPL, quantity=15, averageBuyPrice=160.00, currentPrice=175.00,
                //   invested=2400.00, currentValue=2625.00, pnl=225.00, pnlPercent=9.38, isProfit=true

                Map<String, Object> item = new HashMap<>();
                item.put("currentPrice", currentValueOfPrice);
                item.put("symbol", holding.getSymbol());
                item.put("quantity", holding.getQuantity());
                item.put("averageBuyPrice", holding.getAverageBuyPrice());
                item.put("currentValue", holdingValues);
                item.put("invested", invested);
                item.put("pnl", pnl);
                item.put("pnlPercent", pnlPercent);
                item.put("isProfit", pnl.compareTo(BigDecimal.ZERO) > 0);

                // Add this holding to the portfolio totals:
                // Example: after AAPL -> totalInvested = 2,400.00, currentValue = 2,625.00
                //          after TSLA -> totalInvested = 3,400.00, currentValue = 3,575.00
                portfolioItems.add(item);

                totalInvested = totalInvested.add(invested);
                currentValue = currentValue.add(holdingValues);


            } catch (Exception exception) {
                log.error("Error While Fetching {}  : {}",holding.getSymbol(),exception.getMessage());
            }
        }


        // Portfolio totals (after the loop):
        //   totalPnl        = currentValue - totalInvested        -> 3,575.00 - 3,400.00 = +175.00
        //   totalPnlPercent = (totalPnl / totalInvested) x 100     -> 175.00 / 3,400.00 x 100 = +5.15 %
        // Note: use the totals here, not the average of each stock's %.
        //   (9.38 + -5.00) / 2 = 2.19 %  is WRONG, because AAPL has more money in it than TSLA.

        BigDecimal totalPnl = currentValue.subtract(totalInvested);

        // divide() needs a scale (4 digits) or it throws ArithmeticException when the result
        // never ends (175 / 3,400 = 0.05147058...). If the user has no holdings, totalInvested
        // is 0 and dividing would throw too, so return 0% in that case.
        BigDecimal totalPnlPercent = totalInvested.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : totalPnl.divide(totalInvested, 4, RoundingMode.HALF_UP)   // 0.0515
                          .multiply(BigDecimal.valueOf(100))                // 5.1500
                          .setScale(2, RoundingMode.HALF_UP);               // 5.15

        Map<String,Object> portfolio = new HashMap<>();
        portfolio.put("holdings",portfolioItems);
        portfolio.put("userId",userId);
        portfolio.put("totalPnl",totalPnl);
        portfolio.put("totalPnlPercent",totalPnlPercent);
        portfolio.put("totalInvested",totalInvested);
        portfolio.put("currentValue",currentValue);
        portfolio.put("isProfit", totalPnl.compareTo(BigDecimal.ZERO) > 0);

        return portfolio;

    }


}
