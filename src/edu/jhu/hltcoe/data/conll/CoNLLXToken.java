package edu.jhu.hltcoe.data.conll;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

/**
 * One token from a CoNNL-X formatted file.
 * 
 * From http://ilk.uvt.nl/conll/#dataformat
 * 
 * Data adheres to the following rules:
 * <ul>
 * <li>Data files contain sentences separated by a blank line.</li>
 * <li>A sentence consists of one or tokens, each one starting on a new
 * line.</li>
 * <li>A token consists of ten fields described in the table below. Fields
 * are separated by a single tab character. Space/blank characters are not
 * allowed in within fields</li>
 * <li>All data files will contains these ten fields, although only the ID,
 * FORM, CPOSTAG, POSTAG, HEAD and DEPREL columns are guaranteed to contain
 * non-dummy (i.e. non-underscore) values for all languages.</li>
 * <li>Data files are UTF-8 encoded (Unicode). If you think this will be a
 * problem, have a look <a href="#unicode">here</a>.</li>
 * </ul>
 * 
 * @author mgormley
 * 
 */
public class CoNLLXToken {
    private static final Pattern whitespace = Pattern.compile("\\s+");
    private static final Pattern verticalBar = Pattern.compile("|");
    
    // Field number:    Field name:     Description:
    /** 1    ID  Token counter, starting at 1 for each new sentence. */
    private int id;
    /** 2    FORM    Word form or punctuation symbol. */
    private String form;
    /** 3    LEMMA   Lemma or stem (depending on particular data set) of word form, or an underscore if not available. */
    private String lemma;
    /** 4   CPOSTAG  Coarse-grained part-of-speech tag, where tagset depends on the language. */
    private String cpostag;
    /** 5    POSTAG  Fine-grained part-of-speech tag, where the tagset depends on the language, or identical to the coarse-grained part-of-speech tag if not available. */
    private String postag;
    /** 6    FEATS   Unordered set of syntactic and/or morphological features (depending on the particular language), separated by a vertical bar (|), or an underscore if not available. */
    private Set<String> feats; 
    /** 7    HEAD    Head of the current token, which is either a value of ID or zero ('0'). Note that depending on the original treebank annotation, there may be multiple tokens with an ID of zero. */
    private int head;
    /** 8    DEPREL  Dependency relation to the HEAD. The set of dependency relations depends on the particular language. Note that depending on the original treebank annotation, the dependency relation may be meaningfull or simply 'ROOT'. */
    private String deprel;
    /** 9    PHEAD   Projective head of current token, which is either a value of ID or zero ('0'), or an underscore if not available. Note that depending on the original treebank annotation, there may be multiple tokens an with ID of zero. The dependency structure resulting from the PHEAD column is guaranteed to be projective (but is not available for all languages), whereas the structures resulting from the HEAD column will be non-projective for some sentences of some languages (but is always available). */
    private String phead;
    /** 10   PDEPREL     Dependency relation to the PHEAD, or an underscore if not available. The set of dependency relations depends on the particular language. Note that depending on the original treebank annotation, the dependency relation may be meaningfull or simply 'ROOT'. */
    private String pdeprel;
    
    public CoNLLXToken(String line) {
        // Optional items can take on the dummy value "_".
        // Required: ID, FORM, CPOSTAG, POSTAG, HEAD, DEPREL.
        // Optional: LEMMA, FEATS, PHEAD, PDEPREL.
        
        String[] splits = whitespace.split(line);
        id = Integer.parseInt(splits[0]);
        form = splits[1];
        lemma = fromUnderscoreString(splits[2]);
        cpostag = splits[3];
        postag = splits[4];
        feats = getFeats(fromUnderscoreString(splits[5]));
        head = Integer.parseInt(splits[6]);
        deprel = splits[7];
        phead = fromUnderscoreString(splits[8]);
        pdeprel = fromUnderscoreString(splits[9]);
    }
    
    public CoNLLXToken(int id, String form, String lemma, String cpostag,
            String postag, Set<String> feats, int head, String deprel,
            String phead, String pdeprel) {
        this.id = id;
        this.form = form;
        this.lemma = lemma;
        this.cpostag = cpostag;
        this.postag = postag;
        this.feats = feats;
        this.head = head;
        this.deprel = deprel;
        this.phead = phead;
        this.pdeprel = pdeprel;
    }

    /**
     * Convert from the underscore representation of an optional string to the null representation.
     */
    private static String fromUnderscoreString(String value) {
        if (value.equals("_")) {
            return null;
        } else {
            return value;
        }
    }


    /**
     * Convert from the null representation of an optional string to the underscore representation.
     */
    private static String toUnderscoreString(String value) {
        if (value == null) {
            return "_";
        } else {
            return value;
        }
    }

    private static Set<String> getFeats(String featsStr) {
        if (featsStr == null) {
            return null;
        }
        String[] splits = verticalBar.split(featsStr);
        return new HashSet<String>(Arrays.asList(splits));
    }

    private static String getFeatsString(Set<String> feats) {
        if (feats == null) {
            return "_";
        } else if (feats.size() == 0) {
            return "_";
        } else {
            return StringUtils.join(feats, "|");
        }
    }

    @Override
    public String toString() {
        return "CoNLLXToken [id=" + id + ", form=" + form + ", lemma="
                + lemma + ", cpostag=" + cpostag + ", postag=" + postag
                + ", feats=" + feats + ", head=" + head + ", deprel="
                + deprel + ", phead=" + phead + ", pdeprel=" + pdeprel
                + "]";
    }

    public void write(Writer writer) throws IOException {
        final String sep = " ";
        
        writer.write(String.format("%-3d", id));
        writer.write(sep);
        writer.write(String.format("%-20s", form));
        writer.write(sep);
        writer.write(String.format("%-20s", toUnderscoreString(lemma)));
        writer.write(sep);
        writer.write(String.format("%-5s", cpostag));
        writer.write(sep);
        writer.write(String.format("%-5s", postag));
        writer.write(sep);
        writer.write(String.format("%-20s", getFeatsString(feats)));
        writer.write(sep);
        writer.write(String.format("%-3s", Integer.toString(head)));
        writer.write(sep);
        writer.write(String.format("%-9s", deprel));
        writer.write(sep);
        writer.write(String.format("%-3s", toUnderscoreString(phead)));
        writer.write(sep);
        writer.write(String.format("%-9s", toUnderscoreString(pdeprel)));
        writer.write(sep);
    }

    public int getId() {
        return id;
    }

    public String getForm() {
        return form;
    }

    public String getLemma() {
        return lemma;
    }

    public String getCposTag() {
        return cpostag;
    }

    public String getPosTag() {
        return postag;
    }

    public Set<String> getFeats() {
        return feats;
    }

    public int getHead() {
        return head;
    }

    public String getDepRel() {
        return deprel;
    }

    public String getPhead() {
        return phead;
    }

    public String getPDepRel() {
        return pdeprel;
    }    
    
}