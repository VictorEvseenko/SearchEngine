package searchengine.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.dto.responses.NotOkResponse;
import searchengine.dto.responses.OkResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.exceptions.IncorrectUrlForIndexing;
import searchengine.services.IndexingSiteService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class ApiController
{
    private final StatisticsService statisticsService;
    private final AtomicBoolean indexingProcess = new AtomicBoolean(false);
    private final IndexingSiteService indexingSiteService;
    private final SearchService searchService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @GetMapping("/statistics")
    ResponseEntity<StatisticsResponse> statistics()
    {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity startIndexing()
    {
        if (indexingProcess.get())
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new NotOkResponse(false, "Индексация уже запущена"));
        else
            executor.submit(() -> {
                indexingProcess.set(true);
                indexingSiteService.startIndexing(indexingProcess);
            });
        return ResponseEntity.status(HttpStatus.OK).body(new OkResponse(true));
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity stopIndexing()
    {
        if (!indexingProcess.get())
            return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                    .body(new NotOkResponse(false, "Индексация не запущена"));
        else {
            indexingProcess.set(false);
            return ResponseEntity.status(HttpStatus.OK).body(new OkResponse(true));
        }
    }

    @PostMapping("/indexPage")
    public ResponseEntity indexPage(@RequestParam String url)
    {
        try {
            indexingSiteService.indexingOnePage(url);
        } catch (IncorrectUrlForIndexing ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(new NotOkResponse(false,"Данная страница находится " +
                                    "за пределами сайтов, указанных в конфигурационном файле"));
        }
        return ResponseEntity.status(HttpStatus.OK).body(new OkResponse(true));
    }

    @GetMapping("/search")
    public ResponseEntity<Object> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String site,
            @RequestParam(required = false, defaultValue = "0") Integer offset,
            @RequestParam(required = false, defaultValue = "20") Integer limit)
    {
        if (query == null || query.isBlank())
            return ResponseEntity.badRequest().body(new NotOkResponse(false, "Задан пустой поисковый запрос"));

        return searchService.search(query, site, offset, limit);
    }

}
