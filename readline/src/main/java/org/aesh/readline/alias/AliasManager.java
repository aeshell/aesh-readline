/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aesh.readline.alias;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.aesh.terminal.utils.Config;
import org.aesh.terminal.utils.LoggerUtil;
import org.aesh.terminal.utils.Parser;

/**
 * Manages Aliases
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class AliasManager {

    private final List<Alias> aliases;
    private final Pattern aliasPattern = Pattern.compile("^(alias)\\s+(\\w+)\\s*=\\s*(.*)$");
    private final Pattern listAliasPattern = Pattern.compile("^(alias)((\\s+\\w+)+)$");
    private final Pattern aliasHelpPattern = Pattern.compile("^(" + ALIAS + ")\\s+\\-\\-help$");
    private final Pattern unaliasHelpPattern = Pattern.compile("^(" + UNALIAS + ")\\s+\\-\\-help$");
    private static final String ALIAS = "alias";
    private static final String ALIAS_SPACE = "alias ";
    private static final String UNALIAS = "unalias";
    private File aliasFile;
    private boolean persistAlias = false;

    private static final Logger LOGGER = LoggerUtil.getLogger(AliasManager.class.getName());

    /**
     * Creates a new AliasManager with the specified alias file and persistence setting.
     * If the alias file exists, aliases are read from it during initialization.
     *
     * @param aliasFile the file to read and persist aliases from/to, may be null
     * @param persistAlias if true, aliases will be persisted to the alias file
     * @throws IOException if an I/O error occurs while reading the alias file
     */
    public AliasManager(File aliasFile, boolean persistAlias) throws IOException {
        this.persistAlias = persistAlias;
        aliases = new ArrayList<>();
        if (aliasFile != null) {
            this.aliasFile = aliasFile;
            if (this.aliasFile.isFile())
                readAliasesFromFile();
        }
    }

    /**
     * It is not allowed to create an alias if it conflicts with a command already present
     *
     * @param aliasName name of the alias
     * @return true if there is no conflict
     * @throws AliasConflictException if the alias name conflicts with an existing command
     */
    public boolean verifyNoNewAliasConflict(String aliasName) throws AliasConflictException {
        //default impl just returns true, designed to be overridden
        return true;
    }

    private void readAliasesFromFile() {
        try (BufferedReader br = new BufferedReader(new FileReader(aliasFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(ALIAS)) {
                    try {
                        addAlias(line);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (FileNotFoundException e) {
            LOGGER.warning("Could not find alias file: " + e);
        } catch (IOException e) {
            LOGGER.warning("Could not read alias file: " + e);
        }
    }

    /**
     * Persists all aliases to the alias file.
     * This method writes all current aliases to the configured alias file if persistence
     * is enabled and an alias file was specified. The aliases are sorted before writing.
     * If the file already exists, it is deleted and recreated.
     */
    public void persist() {
        if (persistAlias && aliasFile != null) {

            try {
                //just do it easily and remove the current file
                boolean keepGoing = true;
                if (aliasFile.isFile())
                    keepGoing = aliasFile.delete();

                if (keepGoing) {
                    File parentFile = aliasFile.getParentFile();
                    if (parentFile != null) {
                        parentFile.mkdirs();
                    }
                    keepGoing = aliasFile.createNewFile();
                }

                if (keepGoing) {
                    FileWriter fw = new FileWriter(aliasFile);
                    Collections.sort(aliases); // not very efficient, but it'll do for now...
                    for (Alias a : aliases) {
                        fw.write(ALIAS_SPACE + a.toString() + Config.getLineSeparator());
                    }
                    fw.flush();
                    fw.close();
                }
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Could not persist to alias file:", e);
            }
        }
    }

    void addAlias(String name, String value) {
        Alias alias = new Alias(name, value);
        if (aliases.contains(alias)) {
            aliases.remove(alias);
        }
        aliases.add(alias);
    }

    /**
     * Returns a formatted string containing all defined aliases sorted alphabetically.
     * Each alias is printed on a separate line in the format "alias name='value'".
     *
     * @return a string representation of all aliases, with each alias on a separate line
     */
    @SuppressWarnings("unchecked")
    public String printAllAliases() {
        StringBuilder sb = new StringBuilder();
        /*
         * Collections.sort(aliases); // not very efficient, but it'll do for now...
         * for(Alias a : aliases)
         * sb.append(ALIAS_SPACE).append(a.toString()).append(Config.getLineSeparator());
         *
         * return sb.toString();
         */
        aliases.stream()
                .sorted()
                .forEach(a -> sb
                        .append(ALIAS_SPACE)
                        .append(a.toString())
                        .append(Config.getLineSeparator()));

        return sb.toString();
    }

    /**
     * Retrieves an alias by its name.
     *
     * @param name the name of the alias to retrieve
     * @return an Optional containing the Alias if found, or an empty Optional if not found
     */
    public Optional<Alias> getAlias(String name) {
        return aliases.stream()
                .filter(a -> a.getName().equals(name))
                .findFirst();
    }

    /**
     * Expands an alias if the first word of the input matches an alias name.
     * If a matching alias is found, returns the alias value with the remainder
     * of the input appended.
     *
     * @param input the input string to check for alias expansion
     * @return an Optional containing the expanded command if an alias matches,
     *         or an empty Optional if no alias matches the first word
     */
    public Optional<String> getAliasName(String input) {
        String name = Parser.findFirstWord(input);
        return aliases.stream()
                .filter(a -> a.getName().equals(name))
                .map(alias -> alias.getValue() + input.substring(name.length()))
                .findAny();
    }

    /**
     * Finds all alias names that start with the given prefix.
     * This method is useful for tab completion functionality.
     *
     * @param name the prefix to match against alias names
     * @return a list of alias names that start with the given prefix
     */
    public List<String> findAllMatchingNames(String name) {
        return aliases.stream()
                .filter(a -> a.getName().startsWith(name))
                .map(Alias::getName)
                .collect(Collectors.toList());
    }

    /**
     * Returns a list of all defined alias names.
     *
     * @return a list containing the names of all defined aliases
     */
    public List<String> getAllNames() {
        return aliases.stream()
                .map(Alias::getName)
                .collect(Collectors.toList());
    }

    /**
     * Removes one or more aliases based on the unalias command input.
     * The buffer should contain the unalias command followed by one or more alias names
     * to remove, separated by spaces.
     *
     * @param buffer the unalias command string containing alias names to remove
     * @return null if all aliases were successfully removed, an error message if an alias
     *         was not found, or usage information if the command is invalid
     */
    public String removeAlias(String buffer) {
        if (buffer.trim().equals(UNALIAS))
            return unaliasUsage();
        if (unaliasHelpPattern.matcher(buffer).matches())
            return unaliasUsage();

        buffer = buffer.substring(UNALIAS.length()).trim();

        for (String s : buffer.split(" ")) {
            if (s != null) {
                Optional<Alias> a = getAlias(s.trim());
                if (a.isPresent()) {
                    aliases.remove(a.get());
                } else
                    return "unalias: " + s + ": not found" + Config.getLineSeparator();
            }
        }
        return null;
    }

    /**
     * Parse and add an alias definition.
     *
     * @param line the alias definition string
     * @return an Optional containing an error message if parsing failed, or empty if successful
     */
    public Optional<String> addAlias(String line) {
        String result = parseAlias(line);
        return Optional.ofNullable(result);
    }

    /**
     * Parses an alias command and either creates a new alias, lists specified aliases,
     * or returns all aliases.
     * <p>
     * Supported formats:
     * <ul>
     * <li>"alias" - returns all defined aliases</li>
     * <li>"alias --help" - returns usage information</li>
     * <li>"alias name=value" - creates or updates an alias</li>
     * <li>"alias name1 name2" - lists the specified aliases</li>
     * </ul>
     *
     * @param buffer the alias command string to parse
     * @return null if an alias was successfully created, a string containing alias
     *         definitions when listing, an error message if the command is invalid,
     *         or usage information
     * @deprecated Use {@link #addAlias(String)} instead which returns Optional&lt;String&gt; for error handling.
     */
    @Deprecated
    public String parseAlias(String buffer) {
        if (buffer.trim().equals(ALIAS))
            return printAllAliases();
        if (aliasHelpPattern.matcher(buffer).matches())
            return aliasUsage();
        Matcher aliasMatcher = aliasPattern.matcher(buffer);
        boolean aliasMatched = false;
        if (aliasMatcher.matches()) {
            aliasMatched = true;
            String name = aliasMatcher.group(2);
            String value = aliasMatcher.group(3);
            if (value.startsWith("'")) {
                if (value.endsWith("'"))
                    value = value.substring(1, value.length() - 1);
                else
                    return aliasUsage();
            } else if (value.startsWith("\"")) {
                if (value.endsWith("\""))
                    value = value.substring(1, value.length() - 1);
                else
                    return aliasUsage();
            }
            if (name.contains(" "))
                return aliasUsage();

            try {
                if (verifyNoNewAliasConflict(name)) {
                    addAlias(name, value);
                    return null;
                } else
                    return "Alias " + name + " is in conflict with an existing command";
            } catch (AliasConflictException ace) {
                return ace.getMessage();
            }
        }

        Matcher listMatcher = listAliasPattern.matcher(buffer);
        if (listMatcher.matches()) {
            StringBuilder sb = new StringBuilder();
            for (String s : listMatcher.group(2).trim().split(" ")) {
                if (s != null) {
                    Optional<Alias> a = getAlias(s.trim());
                    if (a.isPresent())
                        sb.append(ALIAS_SPACE).append(a.get().getName()).append("='")
                                .append(a.get().getValue()).append("'").append(Config.getLineSeparator());
                    else
                        sb.append("alias: ").append(s)
                                .append(" : not found").append(Config.getLineSeparator());
                }
            }
            return sb.toString();
        }
        if (!aliasMatched) {
            StringBuilder sb = new StringBuilder();
            sb.append(buffer).append(" is not valid command, make sure alias name is among of [a-zA-Z0-9_]")
                    .append(Config.getLineSeparator());
            return sb.toString();
        }
        return null;
    }

    /**
     * Returns the usage information for the alias command.
     *
     * @return a string containing the alias command usage syntax
     */
    public String aliasUsage() {
        return "alias: usage: alias [name[=value] ... ]" + Config.getLineSeparator();
    }

    /**
     * Returns the usage information for the unalias command.
     *
     * @return a string containing the unalias command usage syntax
     */
    public String unaliasUsage() {
        return "unalias: usage: unalias name [name ...]" + Config.getLineSeparator();
    }

}
