package ch.tutteli.atrium.domain.robstoll.lib.creating.filesystem

import ch.tutteli.atrium.assertions.Assertion
import ch.tutteli.atrium.assertions.AssertionGroup
import ch.tutteli.atrium.assertions.builders.withExplanatoryAssertion
import ch.tutteli.atrium.domain.builders.ExpectImpl
import ch.tutteli.atrium.translations.DescriptionPathAssertion.FAILURE_DUE_TO_LINK_LOOP
import ch.tutteli.atrium.translations.DescriptionPathAssertion.HINT_FOLLOWED_SYMBOLIC_LINK
import ch.tutteli.niok.followSymbolicLink
import java.io.IOException
import java.nio.file.Path
import java.util.*

inline fun explainForResolvedLink(path: Path, resolvedPathAssertionProvider: (realPath: Path) -> Assertion): Assertion {
    val hintList = LinkedList<Assertion>()
    val realPath = addAllLevelResolvedSymlinkHints(path, hintList)
    val resolvedPathAssertion = resolvedPathAssertionProvider(realPath)
    return if (hintList.isNotEmpty()) {
        when (resolvedPathAssertion) {
            //TODO this should be done differently
            is AssertionGroup -> hintList.addAll(resolvedPathAssertion.assertions)
            else -> hintList.add(resolvedPathAssertion)
        }
        ExpectImpl.builder.explanatoryGroup.withDefaultType
            .withAssertions(hintList)
            .build()
    } else {
        resolvedPathAssertion
    }
}

/**
 * Resolves the provided [path] and returns the resolved target (if resolving is possible).
 * Adds explanatory hints for all involved symbolic links to [hintList].
 */
@PublishedApi
internal fun addAllLevelResolvedSymlinkHints(path: Path, hintList: Deque<Assertion>): Path {
    val absolutePath = path.toAbsolutePath().normalize()
    return addAllLevelResolvedSymlinkHints(absolutePath, hintList, LinkedList(), absolutePath)
}

private fun addAllLevelResolvedSymlinkHints(
    absolutePath: Path,
    hintList: Deque<Assertion>,
    visitedList: MutableList<Path>,
    initialPath: Path
): Path {
    var currentPath = absolutePath.root
    visitedList.add(absolutePath)

    for (part in absolutePath) {
        currentPath = currentPath.resolve(part)
        val nextPathAfterFollowSymbolicLink = addOneStepResolvedSymlinkHint(currentPath, hintList)
        if (nextPathAfterFollowSymbolicLink != null) {
            val visitedIndex = visitedList.indexOf(nextPathAfterFollowSymbolicLink)
            if (visitedIndex != -1) {
                //TODO #258 not sure we should do this, this for instance removes followed symlink and could be the reason why the symlink - loop is then wrong
                repeat(hintList.size - visitedIndex) { hintList.removeLast() }
                // add to the list so [hintForLinkLoop] prints this duplicate twice
                visitedList.add(nextPathAfterFollowSymbolicLink)
                hintList.add(hintForLinkLoop(visitedList, visitedIndex))
                return initialPath
            } else {
                currentPath = addAllLevelResolvedSymlinkHints(
                    nextPathAfterFollowSymbolicLink, hintList, visitedList, initialPath
                )
            }
        }
    }
    return currentPath
}

/**
 * If [absolutePath] is surely a symlink, adds an explanatory hint to [hintList] and returns the link target.
 * Return `null` and does not modify [hintList] otherwise.
 */
private fun addOneStepResolvedSymlinkHint(absolutePath: Path, hintList: Deque<Assertion>): Path? {
    //try-catch is used as control flow structure, where within the try we assume absolutePath to be a symbolic link
    return try {
        val nextPath = absolutePath
            .resolveSibling(absolutePath.followSymbolicLink())
            .normalize()

        hintList.add(
            ExpectImpl.builder.explanatory
                .withExplanation(HINT_FOLLOWED_SYMBOLIC_LINK, absolutePath, nextPath)
                .build()
        )
        nextPath
    } catch (e: IOException) {
        // either this is not a link, or we cannot check it. The best we can do is assume it is not a link.
        null
    }
}

private fun hintForLinkLoop(loop: List<Path>, startIndex: Int): Assertion {
    val loopRepresentation = loop.subList(startIndex, loop.size).joinToString(" -> ")
    return ExpectImpl.builder.explanatoryGroup.withWarningType
        .withExplanatoryAssertion(FAILURE_DUE_TO_LINK_LOOP, loopRepresentation)
        .build()
}
