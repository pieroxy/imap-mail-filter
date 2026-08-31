package net.pieroxy.imf.classifier;

import opennlp.tools.doccat.DoccatModel;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubjectClassifierTrainerTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static ClassifierExample example(String subject, ClassifierLabel label) {
    ClassifierExample e = new ClassifierExample();
    e.setSubject(subject);
    e.setLabel(label);
    e.setFrom(Collections.emptyList());
    e.setTo(Collections.emptyList());
    return e;
  }

  @Test
  public void skipsTrainingWhenNotEnoughExamplesOfEachClass() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      examples.add(example("spam subject " + i, ClassifierLabel.SPAM));
      examples.add(example("ham subject " + i, ClassifierLabel.HAM));
    }
    store.append(LocalDate.now(), examples);

    new SubjectClassifierTrainer(store).train();

    assertFalse("pas assez d'exemples (5 < " + SubjectClassifierTrainer.MIN_EXAMPLES_PER_CLASS
        + " par classe) : aucun modèle ne doit être écrit", store.getModelFile().exists());
  }

  @Test
  public void skipsTrainingWhenOneClassIsMissingEntirely() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      examples.add(example("spam subject " + i, ClassifierLabel.SPAM)); // beaucoup de spam, aucun ham
    }
    store.append(LocalDate.now(), examples);

    new SubjectClassifierTrainer(store).train();

    assertFalse("un total élevé ne suffit pas : il faut le minimum des DEUX classes", store.getModelFile().exists());
  }

  @Test
  public void writesAValidModelFileOnceMature() throws Exception {
    ClassifierCorpusStore store = new ClassifierCorpusStore(tmp.getRoot().getAbsolutePath(), "account", 30);
    List<ClassifierExample> examples = new ArrayList<>();
    for (int i = 0; i < SubjectClassifierTrainer.MIN_EXAMPLES_PER_CLASS + 5; i++) {
      examples.add(example("buy cheap stuff now " + i, ClassifierLabel.SPAM));
      examples.add(example("weekly team meeting notes " + i, ClassifierLabel.HAM));
    }
    store.append(LocalDate.now(), examples);

    new SubjectClassifierTrainer(store).train();

    File modelFile = store.getModelFile();
    assertTrue(modelFile.isFile());
    assertTrue("le fichier temporaire ne doit pas traîner après un renommage atomique réussi",
        !new File(modelFile.getParentFile(), modelFile.getName() + ".tmp").exists());
    new DoccatModel(modelFile); // ne doit pas lever : le fichier écrit doit être un modèle valide
  }
}
