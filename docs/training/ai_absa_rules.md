# NOTEBOOK QUALITY & AESTHETICS RULES FOR ABSA TRAINING
## GUIDELINES FOR PRODUCING PUBLICATION-GRADE SCIENTIFIC KAGGLE NOTEBOOKS

Any agent or developer generating code for the ABSA (Aspect-Based Sentiment Analysis) training notebook MUST strictly adhere to the following rules to produce a publication-grade, highly scientific, clean, and meticulously documented Jupyter Notebook.

---

### 1. LANGUAGE & FORMATTING STRICT RULES
*   **100% English Only**: All Markdown cells, code comments, variable naming, docstrings, doc-headers, and log outputs must be in professional technical English. **Zero** Vietnamese text allowed.
*   **Absolutely No Emojis or Icons**: Do **not** use any emojis, icons, or decorative unicode symbols anywhere in Markdown or code comments (e.g., NO 🚀, 📌, ⚠️, ✅, 🧠). Keep it strictly academic, formal, and clean.
*   **Cell-by-Cell Modularity**: Never dump a massive block of code into a single cell. Split code logically into well-defined, executable Jupyter cells.
*   **Markdown Header for Every Cell**: Every single code cell **must** be preceded by a dedicated Markdown cell explaining the scientific/engineering rationale of the upcoming code block. Use clear Markdown headers (##, ###) and clean bullet points.

---

### 2. CODE COMMENTING & READABILITY (HUMAN-TOUCH)
*   **Comprehensive Inline Comments**: Write explicit, concise inline comments for every key logical step, tensor operation, dimension transformation, and hyperparameter declaration.
*   **Tensor Shape Annotation**: For every PyTorch tensor transformation (e.g., pooling, projection, loss calculation), explicitly annotate the tensor shape in comments (e.g., `# Shape: [batch_size, seq_len, hidden_dim]`).
*   **Avoid AI Code Smells**:
    *   Avoid generic/robotic comments like `# This line imports torch` or `# Define the model`. Instead, explain the *why* and *how* (e.g., `# Initialize shared XLM-RoBERTa backbone for cross-lingual feature extraction`).
    *   Do not use overly enthusiastic or conversational tone in comments. Keep it objective, precise, and mathematical.
    *   Avoid over-nesting or overly clever one-liners. Prefer clean, readable, explicit PyTorch code.

---

### 3. SCIENTIFIC & PROFESSIONAL NOTEBOOK STRUCTURE
The notebook must follow a rigorous 8-part academic pipeline:

1.  **System Environment & Reproducibility Setup**:
    *   Configure CUDA device, explicit random seed setting (`seed_everything`) across Python, NumPy, and PyTorch for deterministic execution.
2.  **Configuration & Hyperparameter Centralization**:
    *   Use a dataclass or clean dictionary (`Config` or `HParams`) to manage all hyperparameters (batch size, learning rate, epochs, max length, warm-up ratio, loss weights, random seed).
3.  **Data Ingestion, Custom Dataset & DataLoader**:
    *   PyTorch `Dataset` implementation handling missing aspect labels (`-100` ignore index).
    *   Strict preservation of raw text casing (Cased text input - no lowercasing).
4.  **Multi-Task Deep Learning Architecture**:
    *   Clean `nn.Module` implementation featuring `xlm-roberta-base`, custom `MeanPooling` layer with attention mask, Dropout, and 5 distinct Linear classification heads.
5.  **Loss Function, Optimizer & Learning Rate Schedule**:
    *   Multi-task loss aggregation using `nn.CrossEntropyLoss(ignore_index=-100)`.
    *   **NaN Loss Prevention**: Ensure individual task heads that are 100% missing in a batch (returning `NaN` loss) are filtered out:
        ```python
        if not torch.isnan(head_loss):
            total_loss += head_loss
            active_heads += 1
        loss = total_loss / max(active_heads, 1)
        ```
    *   `AdamW` optimizer with linear warm-up schedule.
    *   **Overfitting Prevention Regularization**: Enforce `weight_decay = 0.05` to `0.1` inside AdamW optimizer initialization to constrain weight values and improve model generalization boundaries.
6.  **Scientific Training & Validation Pipeline**:
    *   Modular train/evaluation functions.
    *   **Encoder Layer Freezing**: Implement layer freezing logic to freeze the first 6 or 8 layers of the shared XLM-RoBERTa encoder during training. Only the final layers and task-specific classification heads must receive gradient updates to prevent overfitting on smaller datasets.
    *   **Early Stopping**: Implement an Early Stopping mechanism to monitor validation macro F1-score with a patience limit of 3 epochs.
    *   Comprehensive metric reporting: Loss, Overall Macro F1-Score, and Individual Aspect F1-Scores.
    *   Model checkpointing based on Validation Macro F1.
7.  **Model Explainability (XAI via Captum)**:
    *   Integrated Gradients attribution pipeline.
    *   Token attribution calculation and Top-15 absolute attribution filtering logic.
    *   **SentencePiece Token Prefix Cleaning**: Strip any ` ` (special whitespace characters) from tokens (`token.replace(" ", "").replace(" ", "")`) before sending JSON strings to the database to ensure clean, human-readable strings.
8.  **Artifact Export & FastAPI Contract Serialization**:
    *   Model weights checkpoint export (`.pth`) and tokenizer state serialization.

---

### 4. EXPECTED OUTPUT CONTRACT
Adhere strictly to all structural, naming, and architectural specifications defined in the workspace:
*   [absa_dataset_guidelines.md](file:///d:/leadora/docs/training/absa_dataset_guidelines.md)
*   [ai_absa_db_integration.md](file:///d:/leadora/docs/training/ai_absa_db_integration.md)
*   [ai_absa_project_plan.md](file:///d:/leadora/docs/training/ai_absa_project_plan.md)
