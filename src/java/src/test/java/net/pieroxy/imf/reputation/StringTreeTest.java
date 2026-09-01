package net.pieroxy.imf.reputation;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StringTreeTest {

  @Test
  public void containsWhatWasAdded() {
    StringTree tree = new StringTree();
    tree.add("example.com");
    tree.add("test.org");

    assertTrue(tree.contains("example.com"));
    assertTrue(tree.contains("test.org"));
  }

  @Test
  public void doesNotContainWhatWasNotAdded() {
    StringTree tree = new StringTree();
    tree.add("example.com");

    assertFalse(tree.contains("example.co"));
    assertFalse(tree.contains("example.com.evil.com"));
    assertFalse(tree.contains("unrelated.net"));
  }

  @Test
  public void isCaseSensitive() {
    StringTree tree = new StringTree();
    tree.add("Example.com");

    assertTrue(tree.contains("Example.com"));
    assertFalse(tree.contains("example.com"));
  }

  @Test
  public void oneStringBeingAPrefixOfAnotherDoesNotConfuseMembership() {
    StringTree tree = new StringTree();
    tree.add("mail");
    tree.add("mail.example.com");

    assertTrue(tree.contains("mail"));
    assertTrue(tree.contains("mail.example.com"));
    assertFalse(tree.contains("mail.example"));
  }

  @Test
  public void addingTheSameValueTwiceReturnsFalseAndDoesNotDoubleCountSize() {
    StringTree tree = new StringTree();
    assertTrue(tree.add("example.com"));
    assertFalse(tree.add("example.com"));

    assertEquals(1, tree.size());
  }

  @Test
  public void sizeReflectsUniqueEntries() {
    StringTree tree = new StringTree();
    tree.add("a.com");
    tree.add("b.com");
    tree.add("a.com");

    assertEquals(2, tree.size());
  }

  @Test
  public void emptyTreeContainsNothing() {
    StringTree tree = new StringTree();
    assertFalse(tree.contains("anything"));
    assertEquals(0, tree.size());
    assertTrue(tree.isEmpty());
  }

  @Test
  public void iteratorYieldsExactlyWhatWasAdded() {
    StringTree tree = new StringTree();
    Set<String> expected = new HashSet<>(Set.of("a.com", "b.com", "mail.example.com", "example.com"));
    tree.addAll(expected);

    Set<String> collected = new HashSet<>();
    tree.forEach(collected::add);

    assertEquals(expected, collected);
  }

  @Test
  public void containsRejectsNonStringObjects() {
    StringTree tree = new StringTree();
    tree.add("example.com");

    assertFalse(tree.contains(42));
  }

  /**
   * Domaines partageant le même suffixe (TLD + domaine parent) — le cas que la compression de
   * chemin sur chaînes inversées est censée bien gérer : "moc.elpmaxe.a"/"moc.elpmaxe.b" une
   * fois inversés partagent un long préfixe commun puis divergent sur le dernier caractère,
   * ce qui force un split d'arête au moment d'insérer le second.
   */
  @Test
  public void domainsSharingATldAndParentDomainAreBothFoundAfterAnEdgeSplit() {
    StringTree tree = new StringTree();
    tree.add("a.example.com");
    tree.add("b.example.com");

    assertTrue(tree.contains("a.example.com"));
    assertTrue(tree.contains("b.example.com"));
    assertFalse(tree.contains("c.example.com"));
    assertEquals(2, tree.size());
  }

  /**
   * "example.com" est un suffixe strict de "sub.example.com" : une fois inversées, la première
   * chaîne est un préfixe complet de la seconde — insérer la seconde après la première doit
   * prolonger l'arête existante en profondeur, pas la scinder.
   */
  @Test
  public void aDomainThatIsASuffixOfAnotherExtendsTheExistingEdge() {
    StringTree tree = new StringTree();
    tree.add("example.com");
    tree.add("sub.example.com");

    assertTrue(tree.contains("example.com"));
    assertTrue(tree.contains("sub.example.com"));
    assertFalse(tree.contains("other.example.com"));
  }

  /** Même chose que ci-dessus mais dans l'ordre inverse : le noeud intermédiaire existe déjà (créé par le premier insert) et doit juste passer terminal=true. */
  @Test
  public void insertingTheShorterSuffixAfterTheLongerOneReusesTheExistingNode() {
    StringTree tree = new StringTree();
    tree.add("sub.example.com");
    tree.add("example.com");

    assertTrue(tree.contains("sub.example.com"));
    assertTrue(tree.contains("example.com"));
    assertEquals(2, tree.size());
  }

  @Test
  public void manySubdomainsOfTheSameParentAreAllDistinctMembers() {
    StringTree tree = new StringTree();
    for (int i = 0; i < 50; i++) {
      tree.add("host" + i + ".dynv6.net");
    }

    assertEquals(50, tree.size());
    assertTrue(tree.contains("host0.dynv6.net"));
    assertTrue(tree.contains("host49.dynv6.net"));
    assertFalse(tree.contains("host50.dynv6.net"));
  }
}
