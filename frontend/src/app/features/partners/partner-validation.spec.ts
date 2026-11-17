import { HttpErrorResponse } from '@angular/common/http';
import { ApiHttpError } from '../../core/models/api-http-error';
import { apiErrorMessage, guaranteeFormErrors, partnerFormErrors, PartnerFormValue, toGuaranteeRequest, toPartnerRequest } from './partner-validation';

const base: PartnerFormValue = {
  partnerType: 'INDIVIDUAL',
  name: 'Ahmed Ben Salah',
  email: '',
  phone: '',
  address: '',
  city: '',
  taxIdentifier: '',
  userId: '',
};
const now = new Date('2030-01-01T00:00:00Z');

describe('partnerFormErrors', () => {
  it('accepts a valid INDIVIDUAL partner', () => {
    expect(partnerFormErrors(base)).toEqual([]);
  });

  it('requires a tax identifier for a COMPANY partner', () => {
    expect(partnerFormErrors({ ...base, partnerType: 'COMPANY', name: 'Sociéte X' })).toContain('Le matricule fiscal est obligatoire pour une société.');
  });

  it('rejects a tax identifier for an INDIVIDUAL partner', () => {
    expect(partnerFormErrors({ ...base, taxIdentifier: '1234567A' })).toContain('Le matricule fiscal n’est pas autorisé pour un particulier.');
  });

  it('requires a non blank name and limits its length', () => {
    expect(partnerFormErrors({ ...base, name: '   ' })).toContain('Le nom ou la raison sociale est obligatoire.');
    expect(partnerFormErrors({ ...base, name: 'a'.repeat(161) })).toContain('Le nom ne peut pas dépasser 160 caractères.');
  });

  it('rejects an invalid e-mail and a malformed user identifier', () => {
    expect(partnerFormErrors({ ...base, email: 'pas-un-email' })).toContain('L’adresse e-mail est invalide.');
    expect(partnerFormErrors({ ...base, userId: '42' })).toContain('L’identifiant du compte doit être un UUID.');
  });

  it('accepts a valid user identifier', () => {
    expect(partnerFormErrors({ ...base, userId: '3f2504e0-4f89-41d3-9a0c-0305e82c3301' })).toEqual([]);
  });
});

describe('toPartnerRequest', () => {
  it('trims values, nulls blanks and drops the tax identifier for INDIVIDUAL partners', () => {
    expect(toPartnerRequest({ ...base, name: '  Ahmed  ', email: ' a@b.tn ', phone: ' ', taxIdentifier: '999' })).toEqual({
      partnerType: 'INDIVIDUAL',
      name: 'Ahmed',
      email: 'a@b.tn',
      phone: null,
      address: null,
      city: null,
      taxIdentifier: null,
      userId: null,
    });
  });

  it('keeps the tax identifier for COMPANY partners', () => {
    expect(toPartnerRequest({ ...base, partnerType: 'COMPANY', taxIdentifier: ' 1234567A ' }).taxIdentifier).toBe('1234567A');
  });
});

describe('guaranteeFormErrors', () => {
  const vehicleId = 'vehicle-1';

  it('accepts a future period of at most 90 days with a reason', () => {
    expect(guaranteeFormErrors(vehicleId, '2030-02-01T10:00', '2030-02-10T10:00', 'Entretien', now)).toEqual([]);
  });

  it('rejects an end date that is not after the start date', () => {
    expect(guaranteeFormErrors(vehicleId, '2030-02-10T10:00', '2030-02-10T10:00', 'Motif', now)).toContain('La date de fin doit être postérieure à la date de début.');
  });

  it('rejects periods longer than 90 days', () => {
    expect(guaranteeFormErrors(vehicleId, '2030-02-01T10:00', '2030-06-01T10:00', 'Motif', now)).toContain('La garantie ne peut pas dépasser 90 jours.');
  });

  it('rejects a start date in the past', () => {
    expect(guaranteeFormErrors(vehicleId, '2029-12-01T10:00', '2029-12-10T10:00', 'Motif', now)).toContain('La garantie doit commencer dans le futur.');
  });

  it('requires a vehicle identifier and a reason', () => {
    const errors = guaranteeFormErrors(' ', '2030-02-01T10:00', '2030-02-10T10:00', '  ', now);
    expect(errors).toContain('L’identifiant du véhicule est obligatoire.');
    expect(errors).toContain('Le motif est obligatoire.');
  });

  it('reports invalid dates without throwing', () => {
    expect(guaranteeFormErrors(vehicleId, '', 'pas-une-date', 'Motif', now)).toEqual([
      'La date de début est invalide.',
      'La date de fin est invalide.',
    ]);
  });
});

describe('toGuaranteeRequest', () => {
  it('converts local dates to ISO strings and trims the identifiers', () => {
    const request = toGuaranteeRequest(' vehicle-1 ', '2030-02-01T10:00', '2030-02-10T10:00', ' Entretien ');
    expect(request.vehicleId).toBe('vehicle-1');
    expect(request.reason).toBe('Entretien');
    expect(request.startsAt).toBe(new Date('2030-02-01T10:00').toISOString());
    expect(request.endsAt).toBe(new Date('2030-02-10T10:00').toISOString());
  });
});

describe('apiErrorMessage', () => {
  it('returns the French message carried by an ApiHttpError', () => {
    expect(apiErrorMessage(new ApiHttpError(409, 'Une garantie active chevauche déjà cette période.'), 'x')).toBe('Une garantie active chevauche déjà cette période.');
  });

  it('maps a known backend code from a raw HTTP error to a French label', () => {
    const error = new HttpErrorResponse({ status: 409, error: { code: 'GUARANTEE_OVERLAP' } });
    expect(apiErrorMessage(error, 'fallback')).toBe('Une garantie active chevauche déjà cette période.');
  });

  it('uses the fallback for unknown or technical codes', () => {
    const error = new HttpErrorResponse({ status: 500, error: { detail: 'UNKNOWN_CODE_XYZ' } });
    expect(apiErrorMessage(error, 'fallback')).toBe('fallback');
    expect(apiErrorMessage(new Error('boom'), 'fallback')).toBe('fallback');
  });
});
