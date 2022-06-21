package dev.gigaherz.guidebook.guidebook.elements;

import dev.gigaherz.guidebook.guidebook.IBookGraphics;
import dev.gigaherz.guidebook.guidebook.book.IParseable;
import dev.gigaherz.guidebook.guidebook.book.ParsingContext;
import dev.gigaherz.guidebook.guidebook.conditions.ConditionContext;
import dev.gigaherz.guidebook.guidebook.drawing.VisualElement;
import dev.gigaherz.guidebook.guidebook.templates.TemplateDefinition;
import dev.gigaherz.guidebook.guidebook.util.Point2I;
import dev.gigaherz.guidebook.guidebook.util.Rect;
import net.minecraft.client.resources.model.Material;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public abstract class Element implements IParseable
{
    public static final int VA_TOP = 0;
    public static final int VA_MIDDLE = 1;
    public static final int VA_BASELINE = 2;
    public static final int VA_BOTTOM = 3;

    public static final int POS_RELATIVE = 0;
    public static final int POS_ABSOLUTE = 1;
    public static final int POS_FIXED = 2;

    /* Positioning mode:
     * 0 = "relative" -- relative to the computed position (offset)
     * 1 = "absolute" -- relative to the containing Panel
     * 2 = "fixed" -- relative to the section
     */
    public int position = 0;

    public int x = 0;
    public int y = 0;
    public int w = 0;
    public int h = 0;

    public int z = 0;

    // in proportion to the element's calculated height
    public float baseline = 7 / 9f; // vanilla font has a baseline 7 pixels from the bottom, with 9px total height

    /* Vertical align mode -- only applicable within a paragraph
     * 0 = top
     * 1 = middle
     * 2 = baseline
     * 3 = bottom
     */
    public int verticalAlignment = VA_BASELINE;

    public Predicate<ConditionContext> condition;
    public boolean conditionResult;

    private static final Pattern WHITESPACE_ONLY = Pattern.compile("^\\s+$");
    protected static boolean isContentNode(Node node)
    {
        if (node.getNodeType() == Node.ELEMENT_NODE)
            return true;
        if (node.getNodeType() == Node.COMMENT_NODE)
            return false;
        if (node.getNodeType() != Node.TEXT_NODE)
            return true;

        var str = node.getTextContent();
        if (str== null || str.length() == 0) return false;

        return !WHITESPACE_ONLY.matcher(str).matches();
    }

    public boolean reevaluateConditions(ConditionContext ctx)
    {
        boolean oldValue = conditionResult;
        conditionResult = condition == null || condition.test(ctx);

        return conditionResult != oldValue;
    }

    public List<VisualElement> measure(IBookGraphics nav, int width, int firstLineWidth)
    {
        return Collections.emptyList();
    }

    public abstract int reflow(List<VisualElement> list, IBookGraphics nav, Rect bounds, Rect page);

    public void findTextures(Set<Material> textures)
    {
    }

    public abstract Element copy();

    @Nullable
    public Element applyTemplate(ParsingContext context, List<Element> sourceElements)
    {
        return copy();
    }

    public boolean supportsPageLevel()
    {
        return false;
    }

    public boolean supportsSpanLevel()
    {
        return true;
    }

    public Point2I applyPosition(Point2I point, Point2I parent)
    {
        return switch (position)
        {
            case POS_RELATIVE -> new Point2I(point.x() + x, point.y() + y);
            case POS_ABSOLUTE -> new Point2I(parent.x() + x, parent.y() + y);
            case POS_FIXED -> new Point2I(x, y);
            default -> new Point2I(point);
        };
    }

    protected <T extends Element> T copy(T other)
    {
        other.position = position;
        other.x = x;
        other.y = y;
        other.z = z;
        other.w = w;
        other.h = h;
        return other;
    }

    @Override
    public void parse(ParsingContext context, NamedNodeMap attributes)
    {
        x = IParseable.getAttribute(attributes, "x", x);
        y = IParseable.getAttribute(attributes, "y", y);
        z = IParseable.getAttribute(attributes, "z", z);
        w = IParseable.getAttribute(attributes, "w", w);
        h = IParseable.getAttribute(attributes, "h", h);

        baseline = IParseable.getAttribute(attributes, "baseline", baseline);

        Node attr = attributes.getNamedItem("align");
        if (attr != null)
        {
            position = switch (attr.getTextContent())
            {
                case "relative" -> 0;
                case "absolute" -> 1;
                case "fixed" -> 2;
                default -> position;
            };
        }

        attr = attributes.getNamedItem("vertical-align");
        if (attr != null)
        {
            verticalAlignment = switch (attr.getTextContent())
            {
                case "top" -> VA_TOP;
                case "middle" -> VA_MIDDLE;
                case "baseline" -> VA_BASELINE;
                case "bottom" -> VA_BOTTOM;
                default -> verticalAlignment;
            };
        }

        attr = attributes.getNamedItem("condition");
        if (attr != null)
        {
            condition = context.getCondition(attr.getTextContent());
        }
    }

    @Override
    public void parseChildNodes(ParsingContext context, NodeList childNodes, Map<String, TemplateDefinition> templates, TextStyle defaultStyle)
    {
    }

    @Override
    public String toString()
    {
        return toString(false);
    }

    public abstract String toString(boolean complete);
}
