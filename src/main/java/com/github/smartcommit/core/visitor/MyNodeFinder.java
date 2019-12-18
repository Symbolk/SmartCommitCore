package com.github.smartcommit.core.visitor;

import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.SimpleType;

import java.util.ArrayList;
import java.util.List;

/**
 * For a given selection range, finds the covered node and the covering node.
 *
 * @since 3.5
 */
public final class MyNodeFinder {
  /** This class defines the actual visitor that finds the node. */
  private static class MyNodeFinderVisitor extends ASTVisitor {
    private int fStart;
    private int fEnd;
    private ASTNode fCoveringNode;
    private ASTNode fCoveredNode;
    private List<ASTNode> fCoveredNodes;

    MyNodeFinderVisitor(int offset, int length) {
      super(true); // include Javadoc tags
      this.fStart = offset;
      this.fEnd = offset + length;
      this.fCoveredNodes = new ArrayList<>();
    }

    @Override
    public boolean preVisit2(ASTNode node) {
      int nodeStart = node.getStartPosition();
      int nodeEnd = nodeStart + node.getLength();
      if (nodeEnd < this.fStart || this.fEnd < nodeStart) {
        return false;
      }
      if (nodeStart <= this.fStart && this.fEnd <= nodeEnd) {
        this.fCoveringNode = node;
      }
      if (this.fStart <= nodeStart && nodeEnd <= this.fEnd) {
        if (this.fCoveringNode == node) { // nodeStart == fStart && nodeEnd == fEnd
          this.fCoveredNode = node;
          return true; // look further for node with same length as parent
        } else if (this.fCoveredNode == null) { // no better found
          this.fCoveredNode = node;
        }
        this.fCoveredNodes.add(node);
        return false;
      }
      return true;
    }
    /**
     * Returns the covered node. If more than one nodes are covered by the selection, the returned
     * node is first covered node found in a top-down traversal of the AST
     *
     * @return ASTNode
     */
    public ASTNode getCoveredNode() {
      return this.fCoveredNode;
    }

    /**
     * Returns the covering node. If more than one nodes are covering the selection, the returned
     * node is last covering node found in a top-down traversal of the AST
     *
     * @return ASTNode
     */
    public ASTNode getCoveringNode() {
      return this.fCoveringNode;
    }

    public List<ASTNode> getCoveredNodes() {
      return this.fCoveredNodes;
    }
  }
  /**
   * Maps a selection to an ASTNode, where the selection is defined using a start and a length. The
   * result node is determined as follows:
   *
   * <ul>
   *   <li>First, tries to find a node whose range is the exactly the given selection. If multiple
   *       matching nodes are found, the innermost is returned.
   *   <li>If no such node exists, then the last node in a preorder traversal of the AST is
   *       returned, where the node range fully contains the selection. If the length is zero, then
   *       ties between adjacent nodes are broken by choosing the right side.
   * </ul>
   *
   * @param root the root node from which the search starts
   * @param start the start of the selection
   * @param length the length of the selection
   * @return the innermost node that exactly matches the selection, or the first node that contains
   *     the selection
   */
  public static ASTNode perform(ASTNode root, int start, int length) {
    org.eclipse.jdt.core.dom.NodeFinder finder =
        new org.eclipse.jdt.core.dom.NodeFinder(root, start, length);
    ASTNode result = finder.getCoveredNode();
    if (result == null || result.getStartPosition() != start || result.getLength() != length) {
      return finder.getCoveringNode();
    }
    return result;
  }

  /**
   * Maps a selection to an ASTNode, where the selection is defined using a source range. Calls
   * <code>perform(root, range.getOffset(), range.getLength())</code>.
   *
   * @param root the root node from which the search starts
   * @param range the selection range
   * @return the innermost node that exactly matches the selection, or the first node that contains
   *     the selection
   * @see #perform(ASTNode, int, int)
   */
  public static ASTNode perform(ASTNode root, ISourceRange range) {
    return perform(root, range.getOffset(), range.getLength());
  }

  /**
   * Maps a selection to an ASTNode, where the selection is given by a start and a length. The
   * result node is determined as follows:
   *
   * <ul>
   *   <li>If {@link #getCoveredNode()} doesn't find a node, returns <code>null</code>.
   *   <li>Otherwise, iff the selection only contains the covered node and optionally some
   *       whitespace or comments on either side of the node, returns the node.
   *   <li>Otherwise, returns the {@link #getCoveringNode() covering} node.
   * </ul>
   *
   * @param root the root node from which the search starts
   * @param start the start of the selection
   * @param length the length of the selection
   * @param source the source of the compilation unit
   * @return the result node
   * @throws JavaModelException if an error occurs in the Java model
   */
  public static ASTNode perform(ASTNode root, int start, int length, ITypeRoot source)
      throws JavaModelException {
    org.eclipse.jdt.core.dom.NodeFinder finder =
        new org.eclipse.jdt.core.dom.NodeFinder(root, start, length);
    ASTNode result = finder.getCoveredNode();
    if (result == null) return null;
    int nodeStart = result.getStartPosition();
    if (start <= nodeStart && ((nodeStart + result.getLength()) <= (start + length))) {
      IBuffer buffer = source.getBuffer();
      if (buffer != null) {
        IScanner scanner = ToolFactory.createScanner(false, false, false, false);
        try {
          scanner.setSource(buffer.getText(start, length).toCharArray());
          int token = scanner.getNextToken();
          if (token != ITerminalSymbols.TokenNameEOF) {
            int tStart = scanner.getCurrentTokenStartPosition();
            if (tStart == result.getStartPosition() - start) {
              scanner.resetTo(tStart + result.getLength(), length - 1);
              token = scanner.getNextToken();
              if (token == ITerminalSymbols.TokenNameEOF) return result;
            }
          }
        } catch (InvalidInputException e) {
          // ignore
        } catch (IndexOutOfBoundsException e) {
          // https://bugs.eclipse.org/bugs/show_bug.cgi?id=305001
          return null;
        }
      }
    }
    return finder.getCoveringNode();
  }

  private ASTNode fCoveringNode;
  private ASTNode fCoveredNode;
  private List<ASTNode> fCoveredNodes;

  /**
   * Instantiate a new node finder using the given root node, the given start and the given length.
   *
   * @param root the given root node
   * @param start the given start
   * @param length the given length
   */
  public MyNodeFinder(ASTNode root, int start, int length) {
    MyNodeFinderVisitor nodeFinderVisitor = new MyNodeFinderVisitor(start, length);
    root.accept(nodeFinderVisitor);
    this.fCoveredNode = nodeFinderVisitor.getCoveredNode();
    this.fCoveringNode = nodeFinderVisitor.getCoveringNode();
    this.fCoveredNodes = nodeFinderVisitor.getCoveredNodes();
  }
  /**
   * If the AST contains nodes whose range is equal to the selection, returns the innermost of those
   * nodes. Otherwise, returns the first node in a preorder traversal of the AST, where the complete
   * node range is covered by the selection.
   *
   * <p>Example: For a {@link SimpleType} whose name is a {@link } and a selection that equals both
   * nodes' range, the covered node is the <code>SimpleName</code>. But if the selection is expanded
   * to include a whitespace before or after the <code>SimpleType</code>, then the covered node is
   * the <code>SimpleType</code>.
   *
   * @return the covered node, or <code>null</code> if the selection is empty or too short to cover
   *     an entire node
   */
  public ASTNode getCoveredNode() {
    return this.fCoveredNode;
  }

  /**
   * Returns the innermost node that fully contains the selection. A node also contains the
   * zero-length selection on either end.
   *
   * <p>If more than one node covers the selection, the returned node is the last covering node
   * found in a preorder traversal of the AST. This implies that for a zero-length selection between
   * two adjacent sibling nodes, the node on the right is returned.
   *
   * @return the covering node
   */
  public ASTNode getCoveringNode() {
    return this.fCoveringNode;
  }

  public List<ASTNode> getCoveredNodes() {
    return this.fCoveredNodes;
  }
}
