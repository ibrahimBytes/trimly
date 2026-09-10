import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ChangeEvent,
  type ComponentType,
  type ReactNode,
} from 'react';
import {
  AlertTriangle,
  Bell,
  Camera,
  Check,
  ChevronRight,
  Code2,
  Copy,
  CreditCard,
  Eye,
  EyeOff,
  Globe,
  KeyRound,
  Link2,
  Monitor,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  Trash2,
  User,
  X,
} from 'lucide-react';
import {
  setupTwoFactor,
  changePassword,
  disableTwoFactor,
  getTwoFactorStatus,
  verifyTwoFactorSetup,
} from '@/auth/authApi';
import { useAuth } from '@/auth/AuthContext';
import { QRCodeSVG } from 'qrcode.react';

type Section =
  | 'account'
  | 'link-defaults'
  | 'branded-links'
  | 'developer'
  | 'notifications'
  | 'billing';

type NavItem = {
  id: Section;
  label: string;
  description: string;
  icon: ComponentType<{ size?: number; className?: string }>;
};

const NAV_GROUPS: { label: string; items: NavItem[] }[] = [
  {
    label: 'Account',
    items: [
      {
        id: 'account',
        label: 'Profile & security',
        description: 'Personal information and account security',
        icon: User,
      },
    ],
  },
  {
    label: 'Links',
    items: [
      {
        id: 'link-defaults',
        label: 'Link defaults',
        description: 'Defaults for new short links',
        icon: SlidersHorizontal,
      },
      {
        id: 'branded-links',
        label: 'Branded links',
        description: 'Custom domains and branding',
        icon: Link2,
      },
    ],
  },
  {
    label: 'Workspace',
    items: [
      {
        id: 'developer',
        label: 'Developer',
        description: 'API keys and integrations',
        icon: Code2,
      },
      {
        id: 'notifications',
        label: 'Notifications',
        description: 'Email preferences',
        icon: Bell,
      },
      {
        id: 'billing',
        label: 'Plan & billing',
        description: 'Plan, usage and billing',
        icon: CreditCard,
      },
    ],
  },
];

const UNAVAILABLE_SECTIONS = new Set<Section>([
  'link-defaults',
  'branded-links',
  'developer',
  'notifications',
]);

function cx(...classes: Array<string | false | null | undefined>) {
  return classes.filter(Boolean).join(' ');
}

function Card({
  children,
  className,
}: {
  children: ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cx(
        'overflow-hidden rounded-2xl border border-[#E5E7EB] bg-white',
        'shadow-[0_1px_2px_rgba(16,24,40,0.03)]',
        className,
      )}
    >
      {children}
    </div>
  );
}

function Button({
  children,
  onClick,
  variant = 'secondary',
  type = 'button',
  disabled = false,
  className,
}: {
  children: ReactNode;
  onClick?: () => void;
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost';
  type?: 'button' | 'submit';
  disabled?: boolean;
  className?: string;
}) {
  const styles = {
    primary:
      'border-transparent bg-[#167A45] text-white shadow-sm hover:bg-[#12663A]',
    secondary:
      'border-[#D9DDE3] bg-white text-[#202124] hover:bg-[#F8F9FA]',
    danger:
      'border-[#E8C8C4] bg-white text-[#B42318] hover:bg-[#FFF7F6]',
    ghost:
      'border-transparent bg-transparent text-[#667085] hover:bg-[#F2F4F7]',
  };

  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled}
      className={cx(
        'inline-flex min-h-10 items-center justify-center gap-2 rounded-xl border px-4',
        'text-sm font-semibold transition-all',
        'focus:outline-none focus:ring-4 focus:ring-[#167A45]/10',
        'disabled:cursor-not-allowed disabled:opacity-50',
        styles[variant],
        className,
      )}
    >
      {children}
    </button>
  );
}

function StatusBadge({
  children,
  tone = 'neutral',
}: {
  children: ReactNode;
  tone?: 'neutral' | 'success' | 'danger';
}) {
  return (
    <span
      className={cx(
        'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1',
        'text-[11px] font-semibold',
        tone === 'success' &&
          'border-[#CDE7D6] bg-[#F1FAF4] text-[#167A45]',
        tone === 'danger' &&
          'border-[#F0D0CC] bg-[#FFF8F7] text-[#A92B20]',
        tone === 'neutral' &&
          'border-[#E4E7EC] bg-[#F9FAFB] text-[#667085]',
      )}
    >
      {children}
    </span>
  );
}

function Toast({ message }: { message: string }) {
  if (!message) return null;

  return (
    <div
      role="status"
      className="fixed bottom-5 left-1/2 z-[120] flex -translate-x-1/2 items-center gap-2 rounded-xl bg-[#202124] px-4 py-3 text-sm font-semibold text-white shadow-2xl"
    >
      <span className="flex h-5 w-5 items-center justify-center rounded-full bg-[#167A45]">
        <Check size={12} />
      </span>
      {message}
    </div>
  );
}

function PageTitle({
  eyebrow,
  title,
  description,
}: {
  eyebrow?: string;
  title: string;
  description: string;
}) {
  return (
    <div>
      {eyebrow && (
        <p className="mb-2 text-xs font-bold uppercase tracking-[0.12em] text-[#98A2B3]">
          {eyebrow}
        </p>
      )}
      <h1 className="text-[30px] font-bold tracking-[-0.035em] text-[#101828] sm:text-[34px]">
        {title}
      </h1>
      <p className="mt-2 max-w-2xl text-sm leading-6 text-[#667085]">
        {description}
      </p>
    </div>
  );
}

function SectionIntro({
  title,
  description,
}: {
  title: string;
  description: string;
}) {
  return (
    <div className="mb-4">
      <h2 className="text-lg font-bold tracking-[-0.02em] text-[#101828]">
        {title}
      </h2>
      <p className="mt-1 text-sm leading-5 text-[#667085]">{description}</p>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  placeholder,
  type = 'text',
  disabled = false,
  hint,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  type?: string;
  disabled?: boolean;
  hint?: string;
}) {
  return (
    <div>
      <label className="mb-2 block text-sm font-semibold text-[#344054]">
        {label}
      </label>
      <input
        type={type}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        disabled={disabled}
        className={cx(
          'h-11 w-full rounded-xl border bg-white px-3.5 text-sm text-[#101828]',
          'outline-none transition',
          'border-[#D0D5DD] focus:border-[#167A45] focus:ring-4 focus:ring-[#167A45]/10',
          'placeholder:text-[#98A2B3]',
          disabled && 'cursor-not-allowed bg-[#F9FAFB] text-[#98A2B3]',
        )}
      />
      {hint && <p className="mt-1.5 text-xs text-[#98A2B3]">{hint}</p>}
    </div>
  );
}

function SettingRow({
  icon: Icon,
  title,
  description,
  action,
  danger = false,
}: {
  icon: ComponentType<{ size?: number; className?: string }>;
  title: string;
  description: string;
  action?: ReactNode;
  danger?: boolean;
}) {
  return (
    <div className="flex flex-col gap-4 border-t border-[#EEF0F3] px-5 py-5 sm:flex-row sm:items-center sm:justify-between sm:px-6">
      <div className="flex min-w-0 items-start gap-3.5">
        <div
          className={cx(
            'flex h-10 w-10 shrink-0 items-center justify-center rounded-xl',
            danger
              ? 'bg-[#FFF1F0] text-[#B42318]'
              : 'bg-[#F2F8F4] text-[#167A45]',
          )}
        >
          <Icon size={18} />
        </div>
        <div className="min-w-0">
          <p
            className={cx(
              'text-sm font-semibold',
              danger ? 'text-[#A92B20]' : 'text-[#101828]',
            )}
          >
            {title}
          </p>
          <p className="mt-1 max-w-2xl text-sm leading-5 text-[#667085]">
            {description}
          </p>
        </div>
      </div>
      {action && <div className="shrink-0 sm:ml-8">{action}</div>}
    </div>
  );
}

function Toggle({
  checked,
  onChange,
  label,
  disabled = false,
}: {
  checked: boolean;
  onChange: (value: boolean) => void;
  label: string;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      aria-label={label}
      disabled={disabled}
      onClick={() => onChange(!checked)}
      className={cx(
        'relative inline-flex h-7 w-12 shrink-0 items-center rounded-full transition-colors',
        'focus:outline-none focus:ring-4 focus:ring-[#167A45]/10',
        checked ? 'bg-[#167A45]' : 'bg-[#D0D5DD]',
        disabled && 'cursor-not-allowed opacity-50',
      )}
    >
      <span
        className={cx(
          'h-5 w-5 rounded-full bg-white shadow-sm transition-transform',
          checked ? 'translate-x-6' : 'translate-x-1',
        )}
      />
    </button>
  );
}

function AccountSection({ notify }: { notify: (message: string) => void }) {
  const {
    user,
    updateProfile,
    uploadProfilePhoto,
    deleteProfilePhoto,
    signOut,
  } = useAuth();

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [photo, setPhoto] = useState<string | null>(null);
  const [selectedPhoto, setSelectedPhoto] = useState<File | null>(null);
  const [isPhotoSaving, setIsPhotoSaving] = useState(false);

  const [twoFactor, setTwoFactor] = useState(false);
  const [isTwoFactorLoading, setIsTwoFactorLoading] = useState(true);
  const [twoFactorModal, setTwoFactorModal] = useState<
    'setup' | 'recovery' | 'disable' | null
  >(null);
  const [twoFactorSetup, setTwoFactorSetup] = useState<{
    secret: string;
    otpauthUri: string;
  } | null>(null);
  const [twoFactorCode, setTwoFactorCode] = useState('');
  const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
  const [twoFactorError, setTwoFactorError] = useState('');
  const [isTwoFactorSubmitting, setIsTwoFactorSubmitting] = useState(false);

  const [passwordModal, setPasswordModal] = useState(false);
  const [saved, setSaved] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!user) return;
    setName(user.fullName ?? '');
    setEmail(user.email);
    setPhoto(user.profileImageUrl ?? null);
    setSelectedPhoto(null);
  }, [user]);

  useEffect(() => {
    let cancelled = false;

    async function loadTwoFactorStatus() {
      setIsTwoFactorLoading(true);
      try {
        const status = await getTwoFactorStatus();
        if (!cancelled) setTwoFactor(status.enabled);
      } catch {
        if (!cancelled) {
          notify('Unable to load two-factor authentication status.');
        }
      } finally {
        if (!cancelled) setIsTwoFactorLoading(false);
      }
    }

    void loadTwoFactorStatus();
    return () => {
      cancelled = true;
    };
  }, [notify]);

  const startTwoFactorSetup = async () => {
    if (isTwoFactorSubmitting || isTwoFactorLoading) return;

    setTwoFactorError('');
    setTwoFactorCode('');
    setIsTwoFactorSubmitting(true);

    try {
      const setup = await setupTwoFactor();
      setTwoFactorSetup(setup);
      setTwoFactorModal('setup');
    } catch (error) {
      setTwoFactorError(
        error instanceof Error
          ? error.message
          : 'Unable to start two-factor authentication setup.',
      );
    } finally {
      setIsTwoFactorSubmitting(false);
    }
  };

  const verifySetup = async () => {
    const normalizedCode = twoFactorCode.trim();

    if (!/^\d{6}$/.test(normalizedCode)) {
      setTwoFactorError('Enter the 6-digit authentication code.');
      return;
    }

    setTwoFactorError('');
    setIsTwoFactorSubmitting(true);

    try {
      const response = await verifyTwoFactorSetup(normalizedCode);
      setTwoFactor(response.enabled);
      setTwoFactorSetup(null);
      setTwoFactorCode('');
      setRecoveryCodes(response.recoveryCodes);
      setTwoFactorModal('recovery');
    } catch (error) {
      setTwoFactorError(
        error instanceof Error
          ? error.message
          : 'The authentication code is incorrect.',
      );
      setTwoFactorCode('');
    } finally {
      setIsTwoFactorSubmitting(false);
    }
  };

  const disableTwoFactorAuth = async (password: string) => {
    const normalizedCode = twoFactorCode.trim();

    if (!password) {
      setTwoFactorError('Enter your current password.');
      return;
    }

    if (!/^\d{6}$/.test(normalizedCode)) {
      setTwoFactorError('Enter your current 6-digit authenticator code.');
      return;
    }

    setTwoFactorError('');
    setIsTwoFactorSubmitting(true);

    try {
      await disableTwoFactor(password, normalizedCode);
      setTwoFactor(false);
      setTwoFactorModal(null);
      setTwoFactorCode('');
      notify('Two-factor authentication disabled. Please sign in again.');
      await signOut();
    } catch (error) {
      setTwoFactorError(
        error instanceof Error
          ? error.message
          : 'Unable to disable two-factor authentication.',
      );
      setTwoFactorCode('');
    } finally {
      setIsTwoFactorSubmitting(false);
    }
  };

  const handlePhoto = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    const allowed = ['image/jpeg', 'image/png', 'image/gif'];

    if (!allowed.includes(file.type)) {
      notify('Please choose a JPG, PNG, or GIF image.');
      event.target.value = '';
      return;
    }

    if (file.size > 2 * 1024 * 1024) {
      notify('Image must be smaller than 2MB.');
      event.target.value = '';
      return;
    }

    const previewUrl = URL.createObjectURL(file);

    setPhoto((previous) => {
      if (previous?.startsWith('blob:')) URL.revokeObjectURL(previous);
      return previewUrl;
    });

    setSelectedPhoto(file);
    notify('Profile photo selected. Save changes to keep it.');
    event.target.value = '';
  };

  const removeProfilePhoto = async () => {
    if (isPhotoSaving) return;

    try {
      setIsPhotoSaving(true);
      const updatedUser = await deleteProfilePhoto();

      if (photo?.startsWith('blob:')) URL.revokeObjectURL(photo);

      setPhoto(updatedUser.profileImageUrl ?? null);
      setSelectedPhoto(null);
      notify('Profile photo removed.');
    } catch (error) {
      notify(
        error instanceof Error
          ? error.message
          : 'Unable to remove your profile photo.',
      );
    } finally {
      setIsPhotoSaving(false);
    }
  };

  const saveProfile = async () => {
    const trimmedName = name.trim();

    if (!trimmedName) {
      notify('Please enter your name.');
      return;
    }

    if (isPhotoSaving) return;

    try {
      setIsPhotoSaving(true);

      let updatedUser = await updateProfile(trimmedName);

      if (selectedPhoto) {
        updatedUser = await uploadProfilePhoto(selectedPhoto);
        setPhoto(updatedUser.profileImageUrl ?? null);
        setSelectedPhoto(null);
      }

      setName(updatedUser.fullName ?? '');
      setEmail(updatedUser.email);
      setSaved(true);
      notify('Profile updated successfully.');

      window.setTimeout(() => setSaved(false), 1800);
    } catch (error) {
      notify(
        error instanceof Error
          ? error.message
          : 'Unable to update your profile.',
      );
    } finally {
      setIsPhotoSaving(false);
    }
  };

  const initials =
    name
      .trim()
      .split(/\s+/)
      .map((part) => part[0])
      .join('')
      .slice(0, 2)
      .toUpperCase() || 'T';

  return (
    <div className="space-y-8">
      <PageTitle
        eyebrow="Account"
        title="Profile & security"
        description="Keep your personal details up to date and protect your Trimly account."
      />

      {/* Account summary */}
      <Card className="border-[#DCE9E0]">
        <div className="flex flex-col gap-5 bg-[linear-gradient(135deg,#F7FCF8_0%,#FFFFFF_65%)] p-5 sm:flex-row sm:items-center sm:justify-between sm:p-6">
          <div className="flex items-center gap-4">
            <div className="relative">
              <div className="flex h-16 w-16 items-center justify-center overflow-hidden rounded-2xl border border-[#DCE9E0] bg-[#EAF6EE]">
                {photo ? (
                  <img
                    src={photo}
                    alt="Profile"
                    className="h-full w-full object-cover"
                  />
                ) : (
                  <span className="text-lg font-bold text-[#167A45]">
                    {initials}
                  </span>
                )}
              </div>
              <button
                type="button"
                onClick={() => fileRef.current?.click()}
                className="absolute -bottom-1.5 -right-1.5 flex h-7 w-7 items-center justify-center rounded-lg border border-white bg-white text-[#667085] shadow-md transition hover:text-[#167A45]"
                aria-label="Change profile photo"
              >
                <Camera size={13} />
              </button>
            </div>

            <div>
              <p className="text-base font-bold text-[#101828]">
                {name || 'Your account'}
              </p>
              <p className="mt-1 text-sm text-[#667085]">{email}</p>
              <div className="mt-2">
                <StatusBadge tone="success">
                  <span className="h-1.5 w-1.5 rounded-full bg-[#167A45]" />
                  Account active
                </StatusBadge>
              </div>
            </div>
          </div>

          <button
            type="button"
            onClick={() => fileRef.current?.click()}
            className="text-left text-sm font-semibold text-[#167A45] hover:underline sm:text-right"
          >
            Change profile photo
          </button>

          <input
            ref={fileRef}
            type="file"
            accept="image/jpeg,image/png,image/gif"
            onChange={handlePhoto}
            className="hidden"
          />
        </div>
      </Card>

      {/* Profile */}
      <section>
        <SectionIntro
          title="Personal information"
          description="This information is associated with your Trimly account."
        />
        <Card>
          <div className="grid gap-5 p-5 sm:grid-cols-2 sm:p-6">
            <Field
              label="Full name"
              value={name}
              onChange={setName}
              placeholder="Your name"
            />
            <Field
              label="Email address"
              value={email}
              onChange={setEmail}
              type="email"
              disabled
              hint="Your email address is managed by your account."
            />
          </div>

          <div className="flex flex-col gap-3 border-t border-[#EEF0F3] bg-[#FCFCFD] px-5 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-6">
            <p className="text-xs text-[#667085]">
              {selectedPhoto
                ? 'You have an unsaved profile photo.'
                : 'Changes are saved to your account immediately.'}
            </p>
            <Button
              variant="primary"
              onClick={saveProfile}
              disabled={isPhotoSaving}
            >
              {isPhotoSaving ? 'Saving…' : saved ? <><Check size={15} /> Saved</> : 'Save changes'}
            </Button>
          </div>
        </Card>
      </section>

      {/* Security */}
      <section>
        <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <SectionIntro
            title="Security"
            description="Protect your account and control how you sign in."
          />
          <StatusBadge tone={twoFactor ? 'success' : 'neutral'}>
            <ShieldCheck size={12} />
            {isTwoFactorLoading
              ? 'Checking security'
              : twoFactor
                ? '2FA enabled'
                : '2FA not enabled'}
          </StatusBadge>
        </div>

        <Card>
          <SettingRow
            icon={KeyRound}
            title="Password"
            description="Update your password regularly and use one you don't use elsewhere."
            action={
              <Button onClick={() => setPasswordModal(true)}>
                Change password
              </Button>
            }
          />

          <SettingRow
            icon={ShieldCheck}
            title="Two-factor authentication"
            description={
              twoFactor
                ? 'Your account requires an authenticator code when you sign in.'
                : 'Add an authenticator app for an additional layer of security.'
            }
            action={
              isTwoFactorLoading ? (
                <div className="h-7 w-12 animate-pulse rounded-full bg-[#EAECF0]" />
              ) : (
                <Toggle
                  checked={twoFactor}
                  disabled={isTwoFactorSubmitting}
                  onChange={(value) => {
                    if (value) {
                      void startTwoFactorSetup();
                    } else {
                      setTwoFactorError('');
                      setTwoFactorCode('');
                      setTwoFactorModal('disable');
                    }
                  }}
                  label="Two-factor authentication"
                />
              )
            }
          />

          <SettingRow
            icon={Monitor}
            title="Active sessions"
            description="Review devices currently signed in to your account."
            action={<StatusBadge>Coming soon</StatusBadge>}
          />
        </Card>
      </section>

      {/* Photo removal */}
      {photo && (
        <div className="flex items-center justify-between rounded-xl border border-[#F0D0CC] bg-[#FFF9F8] px-4 py-3">
          <div>
            <p className="text-sm font-semibold text-[#A92B20]">
              Remove profile photo
            </p>
            <p className="mt-1 text-xs text-[#B76A63]">
              Your account will use your initials instead.
            </p>
          </div>
          <Button
            variant="danger"
            onClick={() => void removeProfilePhoto()}
            disabled={isPhotoSaving}
          >
            Remove
          </Button>
        </div>
      )}

      {/* Danger zone */}
      <section>
        <SectionIntro
          title="Danger zone"
          description="Permanent account actions. These actions should be used carefully."
        />
        <Card className="border-[#F0D0CC]">
          <SettingRow
            icon={Trash2}
            title="Delete account"
            description="Permanently delete your account, short links, and associated data."
            danger
            action={<StatusBadge tone="danger">Coming soon</StatusBadge>}
          />
        </Card>
      </section>

      {passwordModal && (
        <PasswordDialog
          onClose={() => setPasswordModal(false)}
          onSuccess={() => {
            setPasswordModal(false);
            notify('Password updated successfully.');
          }}
        />
      )}

      {twoFactorModal === 'setup' && twoFactorSetup && (
        <TwoFactorSetupDialog
          setup={twoFactorSetup}
          code={twoFactorCode}
          error={twoFactorError}
          isSubmitting={isTwoFactorSubmitting}
          onCodeChange={(value) => {
            setTwoFactorCode(value.replace(/\D/g, '').slice(0, 6));
            if (twoFactorError) setTwoFactorError('');
          }}
          onVerify={verifySetup}
          onClose={() => {
            if (isTwoFactorSubmitting) return;
            setTwoFactorModal(null);
            setTwoFactorSetup(null);
            setTwoFactorCode('');
            setTwoFactorError('');
          }}
        />
      )}

      {twoFactorModal === 'recovery' && (
        <TwoFactorRecoveryCodesDialog
          codes={recoveryCodes}
          onContinue={async () => {
            if (isTwoFactorSubmitting) return;
            setTwoFactorModal(null);
            setRecoveryCodes([]);
            notify('Two-factor authentication enabled. Please sign in again.');
            await signOut();
          }}
        />
      )}

      {twoFactorModal === 'disable' && (
        <TwoFactorDisableDialog
          code={twoFactorCode}
          error={twoFactorError}
          isSubmitting={isTwoFactorSubmitting}
          onCodeChange={(value) => {
            setTwoFactorCode(value.replace(/\D/g, '').slice(0, 6));
            if (twoFactorError) setTwoFactorError('');
          }}
          onConfirm={disableTwoFactorAuth}
          onClose={() => {
            if (isTwoFactorSubmitting) return;
            setTwoFactorModal(null);
            setTwoFactorCode('');
            setTwoFactorError('');
          }}
        />
      )}
    </div>
  );
}

function UnavailableSection({
  title,
  description,
  icon: Icon,
  message,
  detail,
}: {
  title: string;
  description: string;
  icon: ComponentType<{ size?: number; className?: string }>;
  message: string;
  detail?: string;
}) {
  return (
    <div className="space-y-5">
      <PageTitle title={title} description={description} />

      <Card>
        <div className="flex flex-col gap-5 p-6 sm:p-8">
          <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-[#F2F4F7] text-[#667085]">
            <Icon size={20} />
          </div>

          <div>
            <div className="flex flex-wrap items-center gap-2">
              <h2 className="text-base font-bold text-[#101828]">{title}</h2>
              <StatusBadge>Coming soon</StatusBadge>
            </div>
            <p className="mt-2 max-w-2xl text-sm leading-6 text-[#667085]">
              {message}
            </p>
            {detail && (
              <p className="mt-2 max-w-2xl text-sm leading-6 text-[#98A2B3]">
                {detail}
              </p>
            )}
          </div>

          <div className="flex items-start gap-3 rounded-xl bg-[#F9FAFB] p-4">
            <Sparkles size={16} className="mt-0.5 shrink-0 text-[#167A45]" />
            <p className="text-xs leading-5 text-[#667085]">
              This area will become configurable in a future Trimly release.
            </p>
          </div>
        </div>
      </Card>
    </div>
  );
}

function LinkDefaultsSection() {
  return (
    <UnavailableSection
      title="Link defaults"
      description="Choose how newly created short links should behave."
      icon={SlidersHorizontal}
      message="Default expiration and alias-length preferences aren't configurable in the current Trimly release."
      detail="Individual links can still use the options available when you create them."
    />
  );
}

function BrandedLinksSection() {
  return (
    <UnavailableSection
      title="Branded links"
      description="Use your own domain for short links that match your brand."
      icon={Globe}
      message="Custom domains aren't available in the current Trimly release."
      detail="Domain verification and branded link routing will be introduced in a future release."
    />
  );
}

function DeveloperSection() {
  return (
    <UnavailableSection
      title="Developer"
      description="Manage programmatic access and integrations."
      icon={Code2}
      message="API access and API keys aren't available in the current Trimly release."
      detail="The current application does not expose programmatic access yet."
    />
  );
}

function NotificationsSection() {
  return (
    <UnavailableSection
      title="Notifications"
      description="Choose which Trimly notifications you receive."
      icon={Bell}
      message="Notification preferences aren't configurable in the current Trimly release."
      detail="Security-critical account messages may still be sent when necessary to protect your account."
    />
  );
}

function BillingSection() {
  return (
    <div className="space-y-5">
      <PageTitle
        eyebrow="Workspace"
        title="Plan & billing"
        description="Manage your Trimly plan, usage and billing."
      />

      <Card className="border-[#DCE9E0]">
        <div className="p-6 sm:p-8">
          <div className="flex flex-col gap-6 sm:flex-row sm:items-start sm:justify-between">
            <div className="flex items-start gap-4">
              <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-[#EAF6EE] text-[#167A45]">
                <CreditCard size={21} />
              </div>
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="text-lg font-bold text-[#101828]">
                    Free plan
                  </h2>
                  <StatusBadge tone="success">
                    <Check size={11} />
                    Current plan
                  </StatusBadge>
                </div>
                <p className="mt-2 max-w-xl text-sm leading-6 text-[#667085]">
                  Trimly is currently free to use. Paid plans and billing
                  management aren't available yet.
                </p>
              </div>
            </div>

            <StatusBadge>Billing coming soon</StatusBadge>
          </div>

          <div className="mt-6 grid gap-3 sm:grid-cols-3">
            {[
              ['Plan', 'Free'],
              ['Billing', 'Not enabled'],
              ['Payments', 'Not enabled'],
            ].map(([label, value]) => (
              <div
                key={label}
                className="rounded-xl border border-[#EAECF0] bg-[#FCFCFD] p-4"
              >
                <p className="text-xs font-semibold text-[#98A2B3]">{label}</p>
                <p className="mt-1 text-sm font-bold text-[#344054]">{value}</p>
              </div>
            ))}
          </div>
        </div>
      </Card>
    </div>
  );
}

function Modal({
  title,
  description,
  icon: Icon,
  iconTone = 'green',
  children,
  onClose,
  closeDisabled = false,
  labelledBy,
}: {
  title: string;
  description: string;
  icon: ComponentType<{ size?: number; className?: string }>;
  iconTone?: 'green' | 'red';
  children: ReactNode;
  onClose: () => void;
  closeDisabled?: boolean;
  labelledBy: string;
}) {
  return (
    <div
      className="fixed inset-0 z-[90] flex items-end justify-center bg-[#101828]/45 p-0 backdrop-blur-sm sm:items-center sm:p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby={labelledBy}
    >
      <div className="max-h-[92vh] w-full overflow-y-auto rounded-t-3xl bg-white shadow-[0_24px_80px_rgba(0,0,0,0.2)] sm:max-w-md sm:rounded-3xl">
        <div className="p-5 sm:p-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <div
                className={cx(
                  'flex h-11 w-11 items-center justify-center rounded-2xl',
                  iconTone === 'red'
                    ? 'bg-[#FFF1F0] text-[#B42318]'
                    : 'bg-[#EAF6EE] text-[#167A45]',
                )}
              >
                <Icon size={19} />
              </div>
              <h2
                id={labelledBy}
                className="mt-4 text-lg font-bold tracking-[-0.02em] text-[#101828]"
              >
                {title}
              </h2>
              <p className="mt-1.5 text-sm leading-5 text-[#667085]">
                {description}
              </p>
            </div>

            <button
              type="button"
              onClick={onClose}
              disabled={closeDisabled}
              className="rounded-xl p-2 text-[#98A2B3] hover:bg-[#F2F4F7] disabled:opacity-50"
              aria-label="Close"
            >
              <X size={18} />
            </button>
          </div>

          {children}
        </div>
      </div>
    </div>
  );
}

function TwoFactorSetupDialog({
  setup,
  code,
  error,
  isSubmitting,
  onCodeChange,
  onVerify,
  onClose,
}: {
  setup: { secret: string; otpauthUri: string };
  code: string;
  error: string;
  isSubmitting: boolean;
  onCodeChange: (value: string) => void;
  onVerify: () => void;
  onClose: () => void;
}) {
  const [copied, setCopied] = useState(false);

  const copySecret = async () => {
    try {
      await navigator.clipboard.writeText(setup.secret);
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1600);
    } catch {
      setCopied(false);
    }
  };

  return (
    <Modal
      title="Set up two-factor authentication"
      description="Connect an authenticator app, then confirm the 6-digit code it generates."
      icon={ShieldCheck}
      onClose={onClose}
      closeDisabled={isSubmitting}
      labelledBy="two-factor-setup-title"
    >
      <div className="mt-6 space-y-5">
        <div className="rounded-2xl border border-[#EAECF0] bg-[#F9FAFB] p-4">
          <div className="flex items-center gap-2">
            <span className="flex h-6 w-6 items-center justify-center rounded-full bg-[#167A45] text-xs font-bold text-white">
              1
            </span>
            <p className="text-sm font-bold text-[#344054]">
              Scan the QR code
            </p>
          </div>
          <div className="mt-4 flex justify-center rounded-2xl border border-[#EAECF0] bg-white p-4">
            <QRCodeSVG
              value={setup.otpauthUri}
              size={190}
              level="M"
              includeMargin
              aria-label="Trimly two-factor authentication setup QR code"
            />
          </div>

          <div className="mt-4">
            <p className="text-xs font-semibold text-[#667085]">
              Can't scan? Enter this setup key manually.
            </p>
            <div className="mt-2 flex items-center gap-2">
              <code className="min-w-0 flex-1 break-all rounded-xl border border-[#D0D5DD] bg-white px-3 py-2.5 font-mono text-[11px] text-[#344054]">
                {setup.secret}
              </code>
              <button
                type="button"
                onClick={copySecret}
                className="flex h-10 shrink-0 items-center gap-2 rounded-xl border border-[#D0D5DD] bg-white px-3 text-xs font-semibold text-[#344054] hover:bg-[#F9FAFB]"
                aria-label="Copy setup key"
              >
                {copied ? <Check size={14} /> : <Copy size={14} />}
                <span className="hidden sm:inline">
                  {copied ? 'Copied' : 'Copy'}
                </span>
              </button>
            </div>
          </div>
        </div>

        <div>
          <div className="flex items-center gap-2">
            <span className="flex h-6 w-6 items-center justify-center rounded-full bg-[#167A45] text-xs font-bold text-white">
              2
            </span>
            <label
              htmlFor="two-factor-setup-code"
              className="text-sm font-bold text-[#344054]"
            >
              Enter verification code
            </label>
          </div>

          <input
            id="two-factor-setup-code"
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            value={code}
            onChange={(event) => onCodeChange(event.target.value)}
            disabled={isSubmitting}
            placeholder="000000"
            className="mt-3 h-12 w-full rounded-xl border border-[#D0D5DD] px-3 text-center text-lg font-bold tracking-[0.3em] text-[#101828] outline-none focus:border-[#167A45] focus:ring-4 focus:ring-[#167A45]/10"
          />
        </div>

        {error && (
          <div
            role="alert"
            className="rounded-xl bg-[#FFF6F5] px-3.5 py-3 text-sm font-medium text-[#B42318]"
          >
            {error}
          </div>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button
            variant="primary"
            onClick={onVerify}
            disabled={isSubmitting || code.length !== 6}
          >
            {isSubmitting ? 'Verifying…' : 'Enable 2FA'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

function TwoFactorDisableDialog({
  code,
  error,
  isSubmitting,
  onCodeChange,
  onConfirm,
  onClose,
}: {
  code: string;
  error: string;
  isSubmitting: boolean;
  onCodeChange: (value: string) => void;
  onConfirm: (password: string) => void;
  onClose: () => void;
}) {
  const [password, setPassword] = useState('');

  return (
    <Modal
      title="Turn off two-factor authentication?"
      description="You'll need your current password and authenticator code to confirm this change."
      icon={AlertTriangle}
      iconTone="red"
      onClose={onClose}
      closeDisabled={isSubmitting}
      labelledBy="two-factor-disable-title"
    >
      <div className="mt-6 space-y-4">
        <PasswordInput
          label="Current password"
          value={password}
          onChange={setPassword}
        />

        <div>
          <label
            htmlFor="two-factor-disable-code"
            className="mb-2 block text-sm font-semibold text-[#344054]"
          >
            Authentication code
          </label>
          <input
            id="two-factor-disable-code"
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            value={code}
            onChange={(event) => onCodeChange(event.target.value)}
            disabled={isSubmitting}
            placeholder="000000"
            className="h-12 w-full rounded-xl border border-[#D0D5DD] px-3 text-center text-lg font-bold tracking-[0.3em] text-[#101828] outline-none focus:border-[#167A45] focus:ring-4 focus:ring-[#167A45]/10"
          />
        </div>

        {error && (
          <div
            role="alert"
            className="rounded-xl bg-[#FFF6F5] px-3.5 py-3 text-sm font-medium text-[#B42318]"
          >
            {error}
          </div>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button
            variant="danger"
            onClick={() => onConfirm(password)}
            disabled={
              isSubmitting || password.length === 0 || code.length !== 6
            }
          >
            {isSubmitting ? 'Turning off…' : 'Turn off 2FA'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

function TwoFactorRecoveryCodesDialog({
  codes,
  onContinue,
}: {
  codes: string[];
  onContinue: () => void | Promise<void>;
}) {
  const [copied, setCopied] = useState(false);

  const copyCodes = async () => {
    try {
      await navigator.clipboard.writeText(codes.join('\n'));
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1800);
    } catch {
      setCopied(false);
    }
  };

  return (
    <Modal
      title="Save your recovery codes"
      description="Keep these somewhere safe. They won't be shown again after you leave this screen."
      icon={ShieldCheck}
      onClose={onContinue}
      labelledBy="two-factor-recovery-title"
    >
      <div className="mt-6 space-y-4">
        <div className="rounded-2xl border border-[#DCE9E0] bg-[#F7FCF8] p-4">
          <div className="grid grid-cols-2 gap-2">
            {codes.map((code) => (
              <code
                key={code}
                className="rounded-xl border border-[#D0D5DD] bg-white px-3 py-2.5 text-center font-mono text-xs font-bold text-[#344054]"
              >
                {code}
              </code>
            ))}
          </div>

          <button
            type="button"
            onClick={copyCodes}
            className="mt-4 inline-flex h-11 w-full items-center justify-center gap-2 rounded-xl border border-[#D0D5DD] bg-white text-sm font-semibold text-[#344054] hover:bg-[#F9FAFB]"
          >
            {copied ? <Check size={15} /> : <Copy size={15} />}
            {copied ? 'Copied recovery codes' : 'Copy recovery codes'}
          </button>
        </div>

        <div className="rounded-xl border border-[#F0D0CC] bg-[#FFF9F8] px-4 py-3">
          <p className="text-xs leading-5 text-[#A92B20]">
            Each recovery code can be used only once. Store them somewhere
            secure before continuing.
          </p>
        </div>

        <Button
          variant="primary"
          onClick={onContinue}
          disabled={codes.length === 0}
          className="w-full"
        >
          I saved my recovery codes
        </Button>
      </div>
    </Modal>
  );
}

function PasswordDialog({
  onClose,
  onSuccess,
}: {
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const submit = async () => {
    if (isSubmitting) return;

    setError('');

    if (!current) {
      setError('Enter your current password.');
      return;
    }

    if (next.length < 8) {
      setError('New password must be at least 8 characters.');
      return;
    }

    if (next.length > 72) {
      setError('New password must be at most 72 characters.');
      return;
    }

    if (next !== confirm) {
      setError('New passwords do not match.');
      return;
    }

    setIsSubmitting(true);

    try {
      await changePassword(current, next);
      onSuccess();
    } catch (error) {
      if (
        error instanceof Error &&
        'status' in error &&
        typeof (error as { status?: unknown }).status === 'number'
      ) {
        const status = (error as { status: number }).status;

        if (status === 401) {
          setError('Your current password is incorrect.');
        } else if (status === 400) {
          const message = error.message.trim();
          setError(
            message && message !== 'The request is invalid.'
              ? message
              : 'The password change request is invalid.',
          );
        } else if (status === 403) {
          setError('You are not allowed to change the password.');
        } else {
          setError('Unable to change your password. Please try again.');
        }
      } else if (error instanceof Error) {
        setError(
          error.message ||
            'Unable to change your password. Please try again.',
        );
      } else {
        setError('Unable to change your password. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Modal
      title="Change password"
      description="Choose a strong password that you don't use anywhere else."
      icon={KeyRound}
      onClose={onClose}
      closeDisabled={isSubmitting}
      labelledBy="change-password-title"
    >
      <div className="mt-6 space-y-4">
        <PasswordInput
          label="Current password"
          value={current}
          onChange={setCurrent}
        />
        <PasswordInput
          label="New password"
          value={next}
          onChange={setNext}
        />
        <PasswordInput
          label="Confirm new password"
          value={confirm}
          onChange={setConfirm}
        />

        <p className="rounded-xl bg-[#F9FAFB] px-3.5 py-3 text-xs leading-5 text-[#667085]">
          Use at least 8 characters. Avoid passwords you've used on other
          websites.
        </p>

        {error && (
          <div
            role="alert"
            className="rounded-xl bg-[#FFF6F5] px-3.5 py-3 text-sm font-medium text-[#B42318]"
          >
            {error}
          </div>
        )}

        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button
            variant="primary"
            onClick={submit}
            disabled={isSubmitting}
          >
            {isSubmitting ? 'Updating…' : 'Update password'}
          </Button>
        </div>
      </div>
    </Modal>
  );
}

function PasswordInput({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  const [visible, setVisible] = useState(false);

  return (
    <div>
      <label className="mb-2 block text-sm font-semibold text-[#344054]">
        {label}
      </label>
      <div className="relative">
        <input
          type={visible ? 'text' : 'password'}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          className="h-11 w-full rounded-xl border border-[#D0D5DD] px-3.5 pr-11 text-sm text-[#101828] outline-none focus:border-[#167A45] focus:ring-4 focus:ring-[#167A45]/10"
        />
        <button
          type="button"
          onClick={() => setVisible((current) => !current)}
          className="absolute right-2 top-1/2 -translate-y-1/2 rounded-lg p-1.5 text-[#98A2B3] hover:bg-[#F2F4F7] hover:text-[#344054]"
          aria-label={visible ? 'Hide password' : 'Show password'}
        >
          {visible ? <EyeOff size={16} /> : <Eye size={16} />}
        </button>
      </div>
    </div>
  );
}

export function SettingsPage() {
  const [activeSection, setActiveSection] = useState<Section>('account');
  const [toast, setToast] = useState('');
  const [search, setSearch] = useState('');

  const notify = useCallback((message: string) => {
    setToast(message);
    window.setTimeout(() => setToast(''), 2400);
  }, []);

  const activeItem =
    NAV_GROUPS.flatMap((group) => group.items).find(
      (item) => item.id === activeSection,
    ) ?? NAV_GROUPS[0].items[0];

  const filteredGroups = NAV_GROUPS.map((group) => ({
    ...group,
    items: group.items.filter((item) => {
      const query = search.trim().toLowerCase();
      if (!query) return true;
      return (
        item.label.toLowerCase().includes(query) ||
        item.description.toLowerCase().includes(query)
      );
    }),
  })).filter((group) => group.items.length > 0);

  const renderSection = () => {
    switch (activeSection) {
      case 'account':
        return <AccountSection notify={notify} />;
      case 'link-defaults':
        return <LinkDefaultsSection />;
      case 'branded-links':
        return <BrandedLinksSection />;
      case 'developer':
        return <DeveloperSection />;
      case 'notifications':
        return <NotificationsSection />;
      case 'billing':
        return <BillingSection />;
      default:
        return null;
    }
  };

  return (
    <main className="min-h-[calc(100vh-64px)] bg-[#F7F8FA]">
      <div className="mx-auto w-full max-w-7xl px-4 py-5 sm:px-6 lg:px-8 lg:py-8">
        {/* Header */}
        <header className="mb-6 lg:mb-8">
          <div className="mb-3 flex items-center gap-2 text-xs font-medium text-[#98A2B3]">
            <span>Workspace</span>
            <ChevronRight size={13} />
            <span className="text-[#667085]">Settings</span>
          </div>

          <div className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
            <PageTitle
              title="Settings"
              description="Manage your account, security, links and workspace preferences."
            />

            <div className="relative w-full lg:w-80">
              <Search
                size={17}
                className="pointer-events-none absolute left-3.5 top-1/2 -translate-y-1/2 text-[#98A2B3]"
              />
              <input
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder="Search settings"
                aria-label="Search settings"
                className="h-11 w-full rounded-xl border border-[#D0D5DD] bg-white pl-10 pr-3.5 text-sm text-[#101828] outline-none placeholder:text-[#98A2B3] focus:border-[#167A45] focus:ring-4 focus:ring-[#167A45]/10"
              />
            </div>
          </div>
        </header>

        {/* Mobile category navigation */}
        <div className="mb-6 overflow-x-auto lg:hidden">
          <nav
            aria-label="Settings categories"
            className="flex min-w-max gap-2"
          >
            {NAV_GROUPS.flatMap((group) => group.items).map((item) => {
              const Icon = item.icon;
              const active = item.id === activeSection;

              return (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => setActiveSection(item.id)}
                  className={cx(
                    'inline-flex h-10 items-center gap-2 rounded-xl border px-3.5 text-sm font-semibold transition',
                    active
                      ? 'border-[#CDE7D6] bg-[#EAF6EE] text-[#167A45]'
                      : 'border-[#E4E7EC] bg-white text-[#667085] hover:bg-[#F9FAFB]',
                  )}
                >
                  <Icon size={15} />
                  {item.label}
                  {UNAVAILABLE_SECTIONS.has(item.id) && (
                    <span className="text-[10px] font-bold text-[#98A2B3]">
                      Soon
                    </span>
                  )}
                </button>
              );
            })}
          </nav>
        </div>

        <div className="grid grid-cols-1 gap-8 lg:grid-cols-[230px_minmax(0,1fr)]">
          {/* Desktop navigation */}
          <aside className="hidden lg:block">
            <nav
              aria-label="Settings navigation"
              className="sticky top-6"
            >
              <div className="mb-4 px-2">
                <p className="text-[11px] font-bold uppercase tracking-[0.12em] text-[#98A2B3]">
                  Settings
                </p>
              </div>

              <div className="space-y-5">
                {filteredGroups.map((group) => (
                  <div key={group.label}>
                    <p className="mb-1.5 px-2 text-xs font-semibold text-[#98A2B3]">
                      {group.label}
                    </p>

                    <div className="space-y-1">
                      {group.items.map((item) => {
                        const Icon = item.icon;
                        const active = item.id === activeSection;

                        return (
                          <button
                            key={item.id}
                            type="button"
                            onClick={() => setActiveSection(item.id)}
                            aria-current={active ? 'page' : undefined}
                            className={cx(
                              'group flex w-full items-center gap-3 rounded-xl px-2.5 py-2.5 text-left transition',
                              active
                                ? 'bg-white shadow-[0_1px_4px_rgba(16,24,40,0.07)]'
                                : 'hover:bg-white/70',
                            )}
                          >
                            <span
                              className={cx(
                                'flex h-9 w-9 shrink-0 items-center justify-center rounded-xl',
                                active
                                  ? 'bg-[#EAF6EE] text-[#167A45]'
                                  : 'text-[#98A2B3] group-hover:bg-[#F2F4F7] group-hover:text-[#667085]',
                              )}
                            >
                              <Icon size={17} />
                            </span>

                            <span className="min-w-0">
                              <span
                                className={cx(
                                  'block text-sm font-semibold',
                                  active
                                    ? 'text-[#167A45]'
                                    : 'text-[#344054]',
                                )}
                              >
                                {item.label}
                              </span>
                              <span className="mt-0.5 block truncate text-[11px] text-[#98A2B3]">
                                {item.description}
                              </span>
                            </span>

                            {UNAVAILABLE_SECTIONS.has(item.id) && (
                              <span className="ml-auto shrink-0 rounded-full bg-[#F2F4F7] px-1.5 py-0.5 text-[9px] font-bold text-[#98A2B3]">
                                Soon
                              </span>
                            )}
                          </button>
                        );
                      })}
                    </div>
                  </div>
                ))}
              </div>

              <div className="mt-6 rounded-2xl border border-[#E4E7EC] bg-white p-4">
                <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-[#EAF6EE] text-[#167A45]">
                  <Sparkles size={16} />
                </div>
                <p className="mt-3 text-sm font-bold text-[#344054]">
                  Need help?
                </p>
                <p className="mt-1 text-xs leading-5 text-[#98A2B3]">
                  Find setup and API guidance in the Trimly documentation.
                </p>
              </div>
            </nav>
          </aside>

          {/* Main content */}
          <section className="min-w-0">
            <div className="mb-4 flex items-center gap-2 text-xs text-[#98A2B3]">
              <span>{activeItem.label}</span>
              {UNAVAILABLE_SECTIONS.has(activeSection) && (
                <>
                  <span>•</span>
                  <span>Coming soon</span>
                </>
              )}
            </div>
            {renderSection()}
          </section>
        </div>
      </div>

      <Toast message={toast} />
    </main>
  );
} 