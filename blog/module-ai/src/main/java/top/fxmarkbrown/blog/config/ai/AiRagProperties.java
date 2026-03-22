package top.fxmarkbrown.blog.config.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "blog.ai.rag")
public class AiRagProperties {

    private boolean enabled = false;

    private boolean indexPublishedOnly = true;

    private int topK = 6;

    private double similarityThreshold = 0.45D;

    private double articleSimilarityThreshold = 0.42D;

    private double globalSimilarityThreshold = 0.5D;

    private int maxChunkChars = 900;

    private int maxContextChars = 5000;

    private boolean syncOnStartup = true;

    private int rerankFetchTopK = 12;

    private boolean mergeAdjacentChunks = true;

    private int mergeMaxGap = 1;

    private int mergeMaxChars = 1800;

    private boolean articleSupplementEnabled = true;

    private int articleOwnMinTopK = 4;

    private int articleSupplementTopK = 2;

    private int articleRecallTopK = 0;

    private int globalRecallTopK = 0;

    private int shortQueryThreshold = 18;

    private int shortQueryRecallBoost = 6;

    private int globalMaxChunksPerArticle = 2;
}
