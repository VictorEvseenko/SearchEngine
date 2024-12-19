package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.model.Page;
import searchengine.model.SiteEntity;
import searchengine.repository.PageRepository;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
@Service
public class LemmaService
{
    private LuceneMorphology luceneMorphology;
    private final PageRepository pageRepository;
    {
        try {
            luceneMorphology = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String, Integer> getLemmasMap(String htmlText)
    {
        Map<String, Integer> lemmas = new HashMap<>();
        String text = Jsoup.parse(htmlText).text();
        String[] words = text.toLowerCase().replaceAll("([^а-я\\s])", " ").trim().split("\\s+");

        for (String word : words) {
            if (word.isBlank())
                continue;
            List<String> wordBaseForms = luceneMorphology.getMorphInfo(word);
            List<String> normalForms = luceneMorphology.getNormalForms(word);
            if (checkWordBaseForm(wordBaseForms))
                continue;
            if (normalForms.isEmpty())
                continue;
            String normalForm = normalForms.get(0);
            if (lemmas.containsKey(normalForm))
                lemmas.put(normalForm, lemmas.get(normalForm) + 1);
            else
                lemmas.put(normalForm, 1);
        }
        return lemmas;
    }

    public Map<String, Integer> getLemmasBySiteEntityForLemmasDB(SiteEntity siteEntity)
    {
        List<Page> pages = pageRepository.findAllBySiteEntity(siteEntity);
        Map<String, Integer> lemmas = new HashMap<>();

        for(Page page : pages)
            refactorLemmasMap(getLemmasMap(page.getContent())).forEach((k, v) -> lemmas.merge(k, v, Integer::sum));
        return lemmas;
    }

    public Map<String, Integer> refactorLemmasMap(Map<String, Integer> lemmas)
    {
        lemmas.replaceAll((k, v) -> 1);
        return lemmas;
    }

    public String getLemmaByWord(String word)
    {
        String preparedWord = word.toLowerCase().replaceAll("[«»0-9a-z°©№]", "").trim();
        if (word.isEmpty())
            return "";

        List<String> normalForms;
        try {
            normalForms = luceneMorphology.getNormalForms(preparedWord);
        } catch (Exception ex) {
            return "";
        }

        List<String> wordBaseForms = luceneMorphology.getMorphInfo(preparedWord);
        if (checkWordBaseForm(wordBaseForms))
            return "";

        return normalForms.get(0);
    }

    private boolean checkWordBaseForm(List<String> wordBaseForms)
    {
        for (String wordBaseForm : wordBaseForms)
            if (wordBaseForm.contains("ПРЕДЛ") || wordBaseForm.contains("МЕЖД") ||
                    wordBaseForm.contains("СОЮЗ") || wordBaseForm.length() == 1)
                return true;

        return false;
    }

}
