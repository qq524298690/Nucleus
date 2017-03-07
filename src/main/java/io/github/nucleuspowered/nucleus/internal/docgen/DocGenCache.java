/*
 * This file is part of Nucleus, licensed under the MIT License (MIT). See the LICENSE.txt file
 * at the root of this project for more details.
 */
package io.github.nucleuspowered.nucleus.internal.docgen;

import com.google.common.collect.Lists;
import io.github.nucleuspowered.nucleus.Nucleus;
import io.github.nucleuspowered.nucleus.internal.annotations.NoCooldown;
import io.github.nucleuspowered.nucleus.internal.annotations.NoCost;
import io.github.nucleuspowered.nucleus.internal.annotations.NoDocumentation;
import io.github.nucleuspowered.nucleus.internal.annotations.NoPermissions;
import io.github.nucleuspowered.nucleus.internal.annotations.NoWarmup;
import io.github.nucleuspowered.nucleus.internal.annotations.Permissions;
import io.github.nucleuspowered.nucleus.internal.annotations.RequireMixinPlugin;
import io.github.nucleuspowered.nucleus.internal.annotations.Since;
import io.github.nucleuspowered.nucleus.internal.command.StandardAbstractCommand;
import io.github.nucleuspowered.nucleus.internal.docgen.annotations.EssentialsEquivalent;
import io.github.nucleuspowered.nucleus.internal.permissions.PermissionInformation;
import io.github.nucleuspowered.nucleus.internal.permissions.SuggestedLevel;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.text.Text;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DocGenCache {

    private final List<CommandDoc> commandDocs = Lists.newArrayList();
    private final List<PermissionDoc> permissionDocs = Lists.newArrayList();
    private final List<TokenDoc> tokenDocs = Lists.newArrayList();
    private final List<EssentialsDoc> essentialsDocs = Lists.newArrayList();

    private final org.slf4j.Logger logger;

    public DocGenCache(org.slf4j.Logger logger) {
        this.logger = logger;
    }

    public List<CommandDoc> getCommandDocs() {
        return commandDocs;
    }

    public List<PermissionDoc> getPermissionDocs() {
        return permissionDocs;
    }

    public List<TokenDoc> getTokenDocs() {
        return tokenDocs;
    }

    public List<EssentialsDoc> getEssentialsDocs() {
        return essentialsDocs;
    }

    public void addCommand(final String moduleID, final StandardAbstractCommand<?> abstractCommand) {
        if (abstractCommand.getClass().isAnnotationPresent(NoDocumentation.class)) {
            return;
        }

        CommandDoc cmd = new CommandDoc();

        String cmdPath = abstractCommand.getCommandPath().replaceAll("\\.", " ");
        cmd.setCommandName(cmdPath);

        cmd.setAliases(String.join(", ", Lists.newArrayList(abstractCommand.getAliases())));

        if (abstractCommand.getRootCommandAliases().length > 0) {
            cmd.setRootAliases(String.join(", ", Arrays.asList(abstractCommand.getRootCommandAliases())));
        }

        cmd.setPermissionbase(abstractCommand.getPermissionHandler().getBase());

        Class<? extends StandardAbstractCommand> cac = abstractCommand.getClass();
        Permissions s = cac.getAnnotation(Permissions.class);
        if (s == null) {
            cmd.setDefaultLevel(cac.isAnnotationPresent(NoPermissions.class) ? SuggestedLevel.USER.name() : SuggestedLevel.ADMIN.name());
        } else {
            cmd.setDefaultLevel(s.suggestedLevel().name());
        }

        cmd.setModule(moduleID);
        cmd.setCooldown(!cac.isAnnotationPresent(NoCooldown.class));
        cmd.setCost(!cac.isAnnotationPresent(NoCost.class));
        cmd.setWarmup(!cac.isAnnotationPresent(NoWarmup.class));
        cmd.setSince(cac.getAnnotation(Since.class));

        RequireMixinPlugin rmp = cac.getAnnotation(RequireMixinPlugin.class);
        cmd.setRequiresMixin(rmp != null && rmp.document() && rmp.value() == RequireMixinPlugin.MixinLoad.MIXIN_ONLY);

        String desc = abstractCommand.getDescription();
        if (!desc.contains(" ")) {
            logger.warn("Cannot generate description for: " + abstractCommand.getAliases()[0] + ": " + desc);
        }
        cmd.setOneLineDescription(desc);

        String extendedDescription = abstractCommand.getExtendedDescription().replace("\n", "|br|").replace("\"", "&quot;");
        if (!extendedDescription.isEmpty()) {
            cmd.setExtendedDescription(extendedDescription);
        }

        List<PermissionDoc> lp = new ArrayList<>();
        abstractCommand.getPermissionHandler().getSuggestedPermissions().forEach((k, v) -> {
            PermissionInformation pi = new PermissionInformation(MessageFormat.format(v.plainDescription, cmd.getCommandName()), v.level);
            lp.add(getPermissionFrom(moduleID, k, pi));
        });

        cmd.setPermissions(lp);
        cmd.setUsageString(abstractCommand.getUsage(Sponge.getServer().getConsole()));
        cmd.setSubcommands(abstractCommand.getChildrenUsage(Sponge.getServer().getConsole()).map(Text::toPlain).orElse(""));
        cmd.setSimpleUsage(abstractCommand.getSimpleUsage(Sponge.getServer().getConsole()));

        // Essentials
        EssentialsEquivalent ee = abstractCommand.getClass().getAnnotation(EssentialsEquivalent.class);
        if (ee != null) {
            List<String> ss = Arrays.asList(ee.value());
            cmd.setEssentialsEquivalents(ss);
            cmd.setEssNotes(ee.notes());
            cmd.setExactEssEquiv(ee.isExact());

            EssentialsDoc doc = new EssentialsDoc();
            doc.setEssentialsCommands(ss);

            int i = cmdPath.lastIndexOf(" ");
            String c;
            if (i > -1) {
                c = cmdPath.substring(0, i) + " ";
            } else {
                c = "";
            }

            List<String> a = Lists.newArrayList(abstractCommand.getAliases()).stream().map(x -> c + x).collect(Collectors.toList());
            if (abstractCommand.getRootCommandAliases().length > 0) {
                a.addAll(Arrays.asList(abstractCommand.getRootCommandAliases()));
            }

            doc.setNucleusEquiv(a);
            doc.setExact(ee.isExact());
            doc.setNotes(ee.notes());
            essentialsDocs.add(doc);
        }

        permissionDocs.addAll(lp);
        commandDocs.add(cmd);
    }

    public void addPermissionDocs(final String moduleID, Map<String, PermissionInformation> msp) {
        msp.forEach((k, v) -> permissionDocs.add(getPermissionFrom(moduleID, k, v)));
    }

    public void addTokenDocs(Set<String> tokens) {
        tokens.forEach(x -> Nucleus.getNucleus().getMessageProvider().getMessageFromKey("nucleus.token." + x.toLowerCase()).ifPresent(y ->
            tokenDocs.add(new TokenDoc().setName(x.toLowerCase()).setDescription(y))
        ));
    }

    private PermissionDoc getPermissionFrom(String module, String k, PermissionInformation v) {
        PermissionDoc perm = new PermissionDoc();
        perm.setModule(module);
        perm.setDescription(v.plainDescription);
        perm.setPermission(k);
        perm.setDefaultLevel(v.level.name());
        return perm;
    }
}
