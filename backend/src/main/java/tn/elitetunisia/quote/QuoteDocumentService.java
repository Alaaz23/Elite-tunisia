package tn.elitetunisia.quote;
public interface QuoteDocumentService {
    byte[] renderCommercialProposal(QuoteSummary persistedSnapshot);
}